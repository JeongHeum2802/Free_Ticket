package wamddu.backend.payment.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import wamddu.backend.event.domain.Event;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.dto.request.CreateOrderRequest;
import wamddu.backend.order.service.OrderService;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.payment.dto.request.ConfirmPaymentRequest;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.user.domain.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 변경 전 단일 @Transactional 구조의 커밋 단계 예외 재현.
 * 실제 Spring 트랜잭션과 H2 DB를 사용하며, 외부 토스 호출만 대체한다.
 * beforeCommit 예외로 확정 롤백을 유도한다. DB 커밋 응답 유실을 재현하는 테스트는 아니다.
 * 테스트 전체에 @Transactional을 붙이지 않아야 서비스 반환 직후 커밋 단계가 실행된다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_commit_failure_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "logging.level.wamddu.backend.payment.service.PaymentService=DEBUG"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentCommitFailureIntegrationTest {
    @Autowired PaymentService paymentService;
    @Autowired OrderService orderService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @MockitoBean TossPaymentsClient tossPaymentsClient;

    @Test
    @Timeout(30)
    @DisplayName("토스 승인 후 커밋 단계가 실패해 롤백되면 승인된 결제가 방치되면 안 된다")
    void shouldCancelApprovedPaymentWhenCommitPhaseRollsBack() {
        var tx = new TransactionTemplate(transactionManager);
        Fixture fixture = tx.execute(status -> {
            User buyer = User.builder().username("Commit failure buyer").password("test-only")
                    .email("commit-failure@example.test").customerKey("commit_failure_customer").build();
            entityManager.persist(buyer);

            Event event = new Event();
            event.setName("Commit failure reproduction");
            event.setStartDate(LocalDate.now().plusDays(1));
            event.setEndDate(LocalDate.now().plusDays(2));
            event.setLocation("Test venue");
            event.setBannerImageUrl("test-banner");
            event.setMainImageUrl("test-main");
            entityManager.persist(event);

            Ticket ticket = new Ticket();
            ticket.setEvent(event);
            ticket.setType("Commit test ticket");
            ticket.setPrice(10000);
            ticket.setTotal_ticket(1);
            ticket.setSold_ticket(0);
            ticket.setBookingEndtime(LocalDateTime.now().plusDays(1));
            entityManager.persist(ticket);
            return new Fixture(buyer.getId(), ticket.getId());
        });

        var orderRequest = new CreateOrderRequest();
        orderRequest.setTicketId(fixture.ticketId());
        orderRequest.setQuantity(1);
        var checkout = orderService.createOrder(orderRequest, fixture.userId());
        String paymentKey = "commit_failure_payment";
        String idempotencyKey = tx.execute(status -> entityManager.createQuery(
                        "select o.idempotencyKey from Order o where o.orderId = :id", String.class)
                .setParameter("id", checkout.orderId()).getSingleResult());

        // 외부 결제 상태는 DB 롤백의 영향을 받지 않는다.
        var tossStatus = new AtomicReference<>("IN_PROGRESS");
        var cancelCalls = new AtomicInteger();
        var completionStatus = new AtomicInteger(-1);
        var commitFailure = new IllegalStateException("INJECTED_BEFORE_COMMIT_FAILURE");
        when(tossPaymentsClient.cancel(eq(paymentKey), anyString(), anyString())).thenAnswer(invocation -> {
            cancelCalls.incrementAndGet();
            tossStatus.set("CANCELED");
            return new TossPaymentsClient.TossPaymentResponse(
                    paymentKey, checkout.orderId(), "CANCELED", "카드", 10000L, null);
        });
        when(tossPaymentsClient.confirm(paymentKey, checkout.orderId(), 10000L, idempotencyKey))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .as("This reproduces the original single-transaction service").isTrue();
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void beforeCommit(boolean readOnly) {
                            // 서비스 본문이 끝난 뒤 실행된다. 저장 실패와 혼동하지 않도록 flush 성공도 확인한다.
                            entityManager.flush();
                            Order order = entityManager.createQuery(
                                            "select o from Order o where o.orderId = :id", Order.class)
                                    .setParameter("id", checkout.orderId()).getSingleResult();
                            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
                            assertThat(entityManager.find(Ticket.class, fixture.ticketId()).getSold_ticket()).isEqualTo(1);
                            assertThat(paymentCount(checkout.orderId())).isEqualTo(1L);
                            System.out.println("[BEFORE_COMMIT] Body and flush succeeded: order=PAID, payments=1, sold=1");
                            System.out.println("[BEFORE_COMMIT] Injecting failure AFTER the service method returned");
                            throw commitFailure;
                        }

                        @Override
                        public void afterCompletion(int status) {
                            completionStatus.set(status);
                        }
                    });
                    tossStatus.set("DONE");
                    System.out.println("[TOSS STUB] Approval succeeded: status=DONE");
                    return new TossPaymentsClient.TossPaymentResponse(
                            paymentKey, checkout.orderId(), "DONE", "카드", 10000L, null);
                });

        Throwable failure = catchThrowable(() -> paymentService.confirm(fixture.userId(),
                new ConfirmPaymentRequest(paymentKey, checkout.orderId(), 10000L)));
        assertThat(failure).as("The injected commit-phase failure must reach the caller unchanged")
                .isSameAs(commitFailure);
        assertThat(completionStatus.get()).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);

        // 종료된 트랜잭션의 영속성 컨텍스트가 아닌 새 트랜잭션으로 DB 결과를 확인한다.
        tx.executeWithoutResult(status -> {
            Order order = entityManager.createQuery("select o from Order o where o.orderId = :id", Order.class)
                    .setParameter("id", checkout.orderId()).getSingleResult();
            int sold = entityManager.find(Ticket.class, fixture.ticketId()).getSold_ticket();
            long payments = paymentCount(checkout.orderId());
            System.out.printf("[DB AFTER ROLLBACK] order=%s, payments=%d, sold=%d%n", order.getStatus(), payments, sold);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(payments).isZero();
            assertThat(sold).isZero();
        });
        System.out.printf("[TOSS STUB AFTER ROLLBACK] status=%s, cancel calls=%d%n", tossStatus.get(), cancelCalls.get());

        // 변경 전: 실제 값 DONE으로 실패한다. 내부 catch가 커밋 예외를 잡지 못했음을 드러낸다.
        assertThat(tossStatus.get())
                .as("An approved payment must not remain DONE after the order transaction rolled back")
                .isEqualTo("CANCELED");
    }

    private long paymentCount(String orderId) {
        return entityManager.createQuery("select count(p) from Payment p where p.order.orderId = :id", Long.class)
                .setParameter("id", orderId).getSingleResult();
    }

    private record Fixture(Long userId, Long ticketId) {}
}
