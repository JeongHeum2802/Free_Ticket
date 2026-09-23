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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import wamddu.backend.event.domain.Event;
import wamddu.backend.global.exception.ApiException;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

/**
 * 변경 전에는 B 주문이 성공해 마지막 검증이 실패하는 회귀 테스트.
 * 실제 Spring 서비스/트랜잭션/H2 DB를 사용하고 외부 토스 호출만 대체한다.
 * 테스트 전체에 @Transactional을 붙이면 서비스의 커밋 경계가 달라지므로 사용하지 않는다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_expiry_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentExpiryIntegrationTest {
    @Autowired PaymentService paymentService;
    @Autowired OrderService orderService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @MockitoBean TossPaymentsClient tossPaymentsClient;

    @Test
    @Timeout(30)
    @DisplayName("A 승인 응답 대기 중 주문이 만료되어도 마지막 1매를 B에게 배정하면 안 된다")
    void shouldKeepLastTicketReservedWhileConfirmationCrossesExpiry() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        Fixture fixture = tx.execute(status -> {
            User a = User.builder().username("Buyer A").password("test-only")
                    .email("expiry-a@example.test").customerKey("expiry_customer_a").build();
            User b = User.builder().username("Buyer B").password("test-only")
                    .email("expiry-b@example.test").customerKey("expiry_customer_b").build();
            entityManager.persist(a);
            entityManager.persist(b);

            Event event = new Event();
            event.setName("Payment expiry reproduction");
            event.setStartDate(LocalDate.now().plusDays(1));
            event.setEndDate(LocalDate.now().plusDays(2));
            event.setLocation("Test venue");
            event.setBannerImageUrl("test-banner");
            event.setMainImageUrl("test-main");
            entityManager.persist(event);

            Ticket ticket = new Ticket();
            ticket.setEvent(event);
            ticket.setType("Last ticket");
            ticket.setPrice(10000);
            ticket.setTotal_ticket(1);
            ticket.setSold_ticket(0);
            ticket.setBookingEndtime(LocalDateTime.now().plusDays(1));
            entityManager.persist(ticket);
            return new Fixture(a.getId(), b.getId(), ticket.getId());
        });

        CreateOrderRequest request = new CreateOrderRequest();
        request.setTicketId(fixture.ticketId());
        request.setQuantity(1);
        var aOrder = orderService.createOrder(request, fixture.aId());
        // 10분을 기다리는 대신 실제 만료 경계를 5초 뒤에 통과시킨다.
        LocalDateTime expiresAt = tx.execute(status -> {
            Order order = entityManager.createQuery("select o from Order o where o.orderId = :id", Order.class)
                    .setParameter("id", aOrder.orderId()).getSingleResult();
            order.setExpiresAt(LocalDateTime.now().plusSeconds(5));
            return order.getExpiresAt();
        });

        String paymentKey = "expiry_" + UUID.randomUUID();
        var approvalStarted = new CountDownLatch(1);
        var releaseApproval = new CountDownLatch(1);
        when(tossPaymentsClient.confirm(paymentKey, aOrder.orderId(), 10000L,
                tx.execute(status -> entityManager.createQuery(
                                "select o.idempotencyKey from Order o where o.orderId = :id", String.class)
                        .setParameter("id", aOrder.orderId()).getSingleResult())))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .as("Toss approval must run outside the database transaction").isFalse();
                    System.out.printf("[A] Toss approval started: %s, expiresAt=%s%n", LocalDateTime.now(), expiresAt);
                    approvalStarted.countDown();
                    if (!releaseApproval.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test did not release Toss approval within 15 seconds");
                    }
                    return new TossPaymentsClient.TossPaymentResponse(
                            paymentKey, aOrder.orderId(), "DONE", "카드", 10000L, null);
                });

        var executor = Executors.newFixedThreadPool(2);
        try {
            var aPayment = executor.submit(() -> paymentService.confirm(fixture.aId(),
                    new ConfirmPaymentRequest(paymentKey, aOrder.orderId(), 10000L)));
            Throwable bFailure;
            try {
                assertThat(approvalStarted.await(4, TimeUnit.SECONDS))
                        .as("A must reach Toss approval before its order expires").isTrue();
                OrderStatus committedStatus = tx.execute(status -> entityManager.createQuery(
                                "select o.status from Order o where o.orderId = :id", OrderStatus.class)
                        .setParameter("id", aOrder.orderId()).getSingleResult());
                assertThat(committedStatus).as("CONFIRMING must already be committed")
                        .isEqualTo(OrderStatus.CONFIRMING);
                Throwable duplicateFailure = catchThrowable(() -> paymentService.confirm(fixture.aId(),
                        new ConfirmPaymentRequest(paymentKey, aOrder.orderId(), 10000L)));
                assertThat(duplicateFailure).isInstanceOf(ApiException.class);
                assertThat(((ApiException) duplicateFailure).getCode()).isEqualTo("INVALID_ORDER_STATUS");
                while (!LocalDateTime.now().isAfter(expiresAt)) {
                    Thread.sleep(25);
                }
                assertThat(aPayment.isDone()).as("A approval must still be in flight").isFalse();

                // 다른 스레드/트랜잭션에서 B 주문을 실행. 잠금 대기도 무한히 기다리지 않는다.
                bFailure = executor.submit(() -> catchThrowable(() -> {
                    var bOrder = orderService.createOrder(request, fixture.bId());
                    System.out.printf("[B] Order ACCEPTED while A approval is pending: %s, at=%s%n",
                            bOrder.orderId(), LocalDateTime.now());
                })).get(7, TimeUnit.SECONDS);
                assertThat(aPayment.isDone()).as("B must finish before A approval is released").isFalse();
                if (bFailure instanceof ApiException apiException) {
                    System.out.printf("[B] Order REJECTED: %s, at=%s%n", apiException.getCode(), LocalDateTime.now());
                }
            } finally {
                releaseApproval.countDown();
            }

            var result = aPayment.get(7, TimeUnit.SECONDS);
            assertThat(result.status()).isEqualTo("DONE");
            assertThat(paymentService.confirm(fixture.aId(),
                    new ConfirmPaymentRequest(paymentKey, aOrder.orderId(), 10000L)))
                    .as("A completed payment retry must return the existing payment")
                    .extracting("orderId", "paymentKey", "amount", "status")
                    .containsExactly(aOrder.orderId(), paymentKey, 10000L, "DONE");
            tx.executeWithoutResult(status -> {
                Ticket ticket = entityManager.find(Ticket.class, fixture.ticketId());
                Long bOrders = entityManager.createQuery(
                                "select count(o) from Order o where o.user.id = :userId", Long.class)
                        .setParameter("userId", fixture.bId()).getSingleResult();
                Order a = entityManager.createQuery("select o from Order o where o.orderId = :id", Order.class)
                        .setParameter("id", aOrder.orderId()).getSingleResult();
                System.out.printf("[DB] total=%d, sold=%d, A=%s, B committed orders=%d%n",
                        ticket.getTotal_ticket(), ticket.getSold_ticket(), a.getStatus(), bOrders);
                assertThat(a.getStatus()).isEqualTo(OrderStatus.PAID);
                assertThat(ticket.getSold_ticket()).isEqualTo(1);
                assertThat(bOrders).isZero();
            });

            // 변경 전: bFailure == null이므로 실패. 수정 후에도 이 기대값은 그대로 유지한다.
            assertThat(bFailure)
                    .as("B must be rejected: A still owns the only ticket while approval is pending, even after expiry")
                    .isInstanceOf(ApiException.class);
            assertThat(((ApiException) bFailure).getCode()).isEqualTo("TICKET_SOLD_OUT");
        } finally {
            releaseApproval.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private record Fixture(Long aId, Long bId, Long ticketId) {}
}
