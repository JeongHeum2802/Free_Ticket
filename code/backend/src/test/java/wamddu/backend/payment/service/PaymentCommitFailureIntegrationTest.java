package wamddu.backend.payment.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.dto.request.ConfirmPaymentRequest;
import wamddu.backend.payment.dto.response.PaymentResponse;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.user.domain.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 토스 승인 후 일시적인 커밋 실패가 발생해도 결제 완료 상태로 복구되어야 한다.
 * 실제 Spring 트랜잭션과 H2 DB를 사용한다. 토스는 Mock, 결제 저장은 실제 Repository로 실행한다.
 * 첫 결제 저장 시 해당 트랜잭션에 실패를 등록하므로 토스 호출 위치나 트랜잭션 개수에 의존하지 않는다.
 * beforeCommit 예외로 확정 롤백을 유도한다. DB 커밋 응답 유실을 재현하는 테스트는 아니다.
 * 테스트 전체에 @Transactional을 붙이지 않아 서비스가 자신의 트랜잭션을 마무리하게 한다.
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
    @MockitoSpyBean PaymentRepository paymentRepository;

    @Test
    @Timeout(30)
    @DisplayName("토스 승인 후 첫 커밋이 롤백되어도 결제와 재고가 한 번만 반영되어야 한다")
    void shouldRecoverApprovedPaymentWhenFirstCommitRollsBack() {
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
        var injectOnce = new AtomicBoolean(true);
        var commitFailure = new IllegalStateException("INJECTED_BEFORE_COMMIT_FAILURE");
        when(tossPaymentsClient.cancel(eq(paymentKey), anyString(), anyString())).thenAnswer(invocation -> {
            cancelCalls.incrementAndGet();
            tossStatus.set("CANCELED");
            return new TossPaymentsClient.TossPaymentResponse(
                    paymentKey, checkout.orderId(), "CANCELED", "카드", 10000L, null);
        });
        when(tossPaymentsClient.confirm(paymentKey, checkout.orderId(), 10000L, idempotencyKey))
                .thenAnswer(invocation -> {
                    tossStatus.set("DONE");
                    System.out.println("[TOSS STUB] Approval succeeded: status=DONE");
                    return new TossPaymentsClient.TossPaymentResponse(
                            paymentKey, checkout.orderId(), "DONE", "카드", 10000L, null);
                });

        doAnswer(invocation -> {
            // 실제 저장을 유지하고, 저장 예외가 아닌 커밋 단계의 예외만 주입한다.
            Object saved = mockingDetails(invocation.getMock()).getMockCreationSettings()
                    .getDefaultAnswer().answer(invocation);
            if (injectOnce.getAndSet(false)) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        // 트랜잭션 본문과 flush가 성공한 뒤 커밋 직전에 실패한다.
                        entityManager.flush();
                        Order order = entityManager.createQuery(
                                        "select o from Order o where o.orderId = :id", Order.class)
                                .setParameter("id", checkout.orderId()).getSingleResult();
                        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
                        assertThat(entityManager.find(Ticket.class, fixture.ticketId()).getSold_ticket()).isEqualTo(1);
                        assertThat(paymentCount(checkout.orderId())).isEqualTo(1L);
                        System.out.println("[BEFORE_COMMIT] Body and flush succeeded: order=PAID, payments=1, sold=1");
                        System.out.println("[BEFORE_COMMIT] Injecting failure after transaction body completed");
                        throw commitFailure;
                    }

                    @Override
                    public void afterCompletion(int status) {
                        completionStatus.set(status);
                        System.out.printf("[FIRST PAYMENT COMMIT] completion=%d (rolled back=%d)%n",
                                status, TransactionSynchronization.STATUS_ROLLED_BACK);
                    }
                });
            }
            return saved;
        }).when(paymentRepository).save(any(Payment.class));

        var response = new AtomicReference<PaymentResponse>();
        Throwable failure = catchThrowable(() -> response.set(paymentService.confirm(fixture.userId(),
                new ConfirmPaymentRequest(paymentKey, checkout.orderId(), 10000L))));

        // 실제 롤백이 발생했는지 확인한 뒤, 변경 전후에 동일한 성공 조건을 검증한다.
        assertThat(completionStatus.get()).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        System.out.printf("[SERVICE RESULT] error=%s, response=%s%n", failure, response.get());
        System.out.printf("[TOSS RESULT] status=%s, cancel calls=%d%n", tossStatus.get(), cancelCalls.get());

        // 종료된 트랜잭션의 영속성 컨텍스트가 아닌 새 트랜잭션으로 DB 결과를 확인한다.
        tx.executeWithoutResult(status -> {
            Order order = entityManager.createQuery("select o from Order o where o.orderId = :id", Order.class)
                    .setParameter("id", checkout.orderId()).getSingleResult();
            int sold = entityManager.find(Ticket.class, fixture.ticketId()).getSold_ticket();
            long payments = paymentCount(checkout.orderId());
            System.out.printf("[DB FINAL] order=%s, payments=%d, sold=%d%n", order.getStatus(), payments, sold);
            assertThat(order.getStatus()).as("Approved payment must be reflected in the order")
                    .isEqualTo(OrderStatus.PAID);
            assertThat(payments).isEqualTo(1L);
            assertThat(sold).isEqualTo(1);
            Payment payment = paymentRepository.findByOrderOrderId(checkout.orderId()).orElseThrow();
            assertThat(payment.getPaymentKey()).isEqualTo(paymentKey);
            assertThat(payment.getAmount()).isEqualTo(10000L);
            assertThat(payment.getStatus()).isEqualTo("DONE");
        });
        assertThat(failure).as("A one-time commit failure must be recovered").isNull();
        assertThat(response.get()).isNotNull();
        assertThat(response.get().status()).isEqualTo("DONE");
        assertThat(response.get().orderId()).isEqualTo(checkout.orderId());
        assertThat(response.get().paymentKey()).isEqualTo(paymentKey);
        assertThat(tossStatus.get()).isEqualTo("DONE");
        assertThat(cancelCalls.get()).isZero();
        verify(tossPaymentsClient, times(1)).confirm(paymentKey, checkout.orderId(), 10000L, idempotencyKey);
    }

    private long paymentCount(String orderId) {
        return entityManager.createQuery("select count(p) from Payment p where p.order.orderId = :id", Long.class)
                .setParameter("id", orderId).getSingleResult();
    }

    private record Fixture(Long userId, Long ticketId) {}
}
