package wamddu.backend.payment.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import wamddu.backend.event.domain.Event;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.order.service.OrderService;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.user.domain.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reservation_cancellation_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "payment.recovery.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(30)
class ReservationCancellationIntegrationTest {
    private static final String ORDER_ID = "ORD-RESERVATION-CANCEL";
    private static final String PAYMENT_KEY = "reservation_payment";
    @Autowired PaymentService service;
    @Autowired OrderService orderService;
    @MockitoSpyBean OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean TossPaymentsClient toss;
    TransactionTemplate tx;
    Long userId;
    Long ticketId;
    String approvalKey;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(manager);
        approvalKey = UUID.randomUUID().toString();
        tx.executeWithoutResult(s -> {
            for (String entity : List.of("Payment", "Order", "Ticket", "Event", "User")) {
                em.createQuery("delete from " + entity).executeUpdate();
            }
            User buyer = User.builder().username("Cancellation buyer").password("test-only")
                    .email("cancellation@example.test").customerKey("cancellation_customer").build();
            em.persist(buyer);
            userId = buyer.getId();
            Event event = new Event();
            event.setName("Cancellation concert");
            event.setStartDate(LocalDate.now().plusDays(1));
            event.setEndDate(LocalDate.now().plusDays(2));
            event.setLocation("Test venue");
            event.setBannerImageUrl("banner");
            event.setMainImageUrl("main");
            em.persist(event);
            Ticket ticket = new Ticket();
            ticket.setEvent(event);
            ticket.setType("Standard");
            ticket.setPrice(10000);
            ticket.setTotal_ticket(10);
            ticket.setSold_ticket(2);
            ticket.setStart_time(LocalDateTime.now().plusDays(1));
            ticket.setBookingEndtime(LocalDateTime.now().plusHours(20));
            em.persist(ticket);
            ticketId = ticket.getId();
            Order order = Order.createPendingOrder(ORDER_ID, buyer, ticketId, event.getId(),
                    2, 10000, approvalKey, 10);
            order.setStatus(OrderStatus.PAID);
            order.setPaymentKey(PAYMENT_KEY);
            order.setPaidAt(LocalDateTime.now().minusMinutes(5));
            em.persist(order);
            em.persist(Payment.createPayment(order, PAYMENT_KEY, 20000L, "카드", "DONE",
                    order.getPaidAt(), "https://example.test/receipt"));
        });
    }

    @Test
    void fullCancellationPreservesHistoryAndRestoresInventoryExactlyOnce() {
        tx.executeWithoutResult(s -> {
            order().setRecoveryAttempts(19);
            order().setRecoveryReviewRequired(true);
        });
        when(toss.getPayment(PAYMENT_KEY)).thenReturn(pg("DONE"));
        when(toss.cancel(eq(PAYMENT_KEY), anyString(), eq("reservation-cancel:" + approvalKey))).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertDb(OrderStatus.CANCELING, "DONE", 2);
            tx.executeWithoutResult(s -> {
                assertThat(order().getRecoveryAttempts()).isZero();
                assertThat(order().isRecoveryReviewRequired()).isFalse();
            });
            return pg("CANCELED");
        });

        assertThat(service.cancelReservation(userId, ORDER_ID).status()).isEqualTo("CANCELED");
        assertThat(service.cancelReservation(userId, ORDER_ID).status()).isEqualTo("CANCELED");

        assertDb(OrderStatus.CANCELED, "CANCELED", 0);
        tx.executeWithoutResult(s -> {
            assertThat(order().getPaidAt()).isNotNull();
            assertThat(order().getNextRecoveryAt()).isNull();
            assertThat(payments.findByOrderOrderId(ORDER_ID).orElseThrow().getAmount()).isEqualTo(20000L);
            assertThat(em.createQuery("select count(o) from Order o", Long.class).getSingleResult()).isEqualTo(1L);
        });
        assertThat(orderService.getMyReservations(userId).reservations()).isEmpty();
        verify(toss, times(1)).cancel(eq(PAYMENT_KEY), anyString(), eq("reservation-cancel:" + approvalKey));
    }

    @Test
    void foreignUserCannotCancelAnotherUsersReservation() {
        assertError(userId + 10000, "ORDER_NOT_FOUND");
        assertDb(OrderStatus.PAID, "DONE", 2);
        verifyNoInteractions(toss);
    }

    @Test
    void performanceThatAlreadyStartedCannotBeCanceled() {
        tx.executeWithoutResult(s -> em.find(Ticket.class, ticketId).setStart_time(LocalDateTime.now().minusSeconds(1)));
        assertError(userId, "CANCELLATION_CLOSED");
        assertDb(OrderStatus.PAID, "DONE", 2);
        verifyNoInteractions(toss);
    }

    @Test
    void virtualAccountRequiresARefundAccountBeforeCancellation() {
        tx.executeWithoutResult(s -> payments.findByOrderOrderId(ORDER_ID).orElseThrow().setMethod("가상계좌"));
        assertError(userId, "UNSUPPORTED_REFUND_METHOD");
        assertDb(OrderStatus.PAID, "DONE", 2);
        verifyNoInteractions(toss);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "CONFIRMING", "EXPIRED", "PAYMENT_FAILED"})
    void unpaidStatusesCannotStartCustomerCancellation(String status) {
        tx.executeWithoutResult(s -> order().setStatus(OrderStatus.valueOf(status)));
        assertError(userId, "INVALID_ORDER_STATUS");
        assertDb(OrderStatus.valueOf(status), "DONE", 2);
        verifyNoInteractions(toss);
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment-key", "amount", "event"})
    void inconsistentLocalReservationCannotStartAnExternalRefund(String mismatch) {
        tx.executeWithoutResult(s -> {
            var payment = payments.findByOrderOrderId(ORDER_ID).orElseThrow();
            switch (mismatch) {
                case "payment-key" -> payment.setPaymentKey("different_payment");
                case "amount" -> payment.setAmount(19999L);
                case "event" -> order().setEvent_id(order().getEvent_id() + 1000);
            }
        });

        assertError(userId, mismatch.equals("event") ? "INVALID_TICKET_DATA" : "PAYMENT_STATE_MISMATCH");

        assertDb(OrderStatus.PAID, "DONE", 2);
        verifyNoInteractions(toss);
    }

    @Test
    void invalidLocalCancellationIsFlaggedForReviewWithoutRepeatedProviderCalls() {
        tx.executeWithoutResult(s -> {
            order().setStatus(OrderStatus.CANCELING);
            order().setNextRecoveryAt(LocalDateTime.now().minusSeconds(1));
            payments.findByOrderOrderId(ORDER_ID).orElseThrow().setAmount(19999L);
        });

        service.recoverPendingPayments();
        service.recoverPendingPayments();

        assertDb(OrderStatus.CANCELING, "DONE", 2);
        tx.executeWithoutResult(s -> {
            assertThat(order().isRecoveryReviewRequired()).isTrue();
            assertThat(order().getNextRecoveryAt()).isNull();
        });
        verifyNoInteractions(toss);
    }

    @Test
    void failedIntentCommitNeverCallsTheProvider() {
        failFirstCommitAt(OrderStatus.CANCELING);
        assertThatThrownBy(() -> service.cancelReservation(userId, ORDER_ID)).isInstanceOf(RuntimeException.class);
        assertDb(OrderStatus.PAID, "DONE", 2);
        verifyNoInteractions(toss);
    }

    @Test
    void lostProviderResponseIsRecoveredFromCommittedCancellationIntent() {
        when(toss.getPayment(PAYMENT_KEY)).thenReturn(pg("DONE"), pg("CANCELED"));
        when(toss.cancel(eq(PAYMENT_KEY), anyString(), anyString()))
                .thenThrow(new IllegalStateException("cancel response lost"));

        assertPending();
        assertDb(OrderStatus.CANCELING, "DONE", 2);
        makeRecoveryDue();
        service.recoverPendingPayments();
        service.recoverPendingPayments();

        assertDb(OrderStatus.CANCELED, "CANCELED", 0);
        verify(toss, times(1)).cancel(eq(PAYMENT_KEY), anyString(), eq("reservation-cancel:" + approvalKey));
    }

    @Test
    void failedFinalCommitKeepsInventoryUntilRecoveryConfirmsRefund() {
        when(toss.getPayment(PAYMENT_KEY)).thenReturn(pg("DONE"), pg("CANCELED"));
        when(toss.cancel(eq(PAYMENT_KEY), anyString(), anyString())).thenReturn(pg("CANCELED"));
        failFirstCommitAt(OrderStatus.CANCELED);

        assertPending();
        assertDb(OrderStatus.CANCELING, "DONE", 2);
        makeRecoveryDue();
        service.recoverPendingPayments();

        assertDb(OrderStatus.CANCELED, "CANCELED", 0);
        verify(toss, times(1)).cancel(eq(PAYMENT_KEY), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "order", "balance", "pending-refund", "refund-total", "partial"})
    void unverifiedProviderRefundCannotReleaseInventory(String invalid) {
        var good = pg("CANCELED");
        var bad = new TossPaymentsClient.TossPaymentResponse(PAYMENT_KEY,
                invalid.equals("order") ? "ORD-OTHER" : ORDER_ID,
                invalid.equals("partial") ? "PARTIAL_CANCELED" : "CANCELED", "카드",
                invalid.equals("amount") ? 19999L : 20000L, null, "test-mid", "KRW",
                invalid.equals("balance") ? 1L : 0L,
                List.of(new TossPaymentsClient.TossPaymentResponse.Cancel(
                        invalid.equals("refund-total") ? 19999L : 20000L,
                        invalid.equals("pending-refund") ? "PENDING" : "DONE", good.cancels().getFirst().canceledAt())));
        when(toss.getPayment(PAYMENT_KEY)).thenReturn(bad);

        assertPending();
        assertDb(OrderStatus.CANCELING, "DONE", 2);
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void partialRemainingBalanceIsNotConvertedIntoAFullCustomerCancellation() {
        var partial = new TossPaymentsClient.TossPaymentResponse(PAYMENT_KEY, ORDER_ID, "DONE", "카드",
                20000L, null, "test-mid", "KRW", 10000L, List.of());
        when(toss.getPayment(PAYMENT_KEY)).thenReturn(partial);
        assertPending();
        assertDb(OrderStatus.CANCELING, "DONE", 2);
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void paidCancellationRemainsVisibleWithoutReservingTheSameStockTwice() {
        when(toss.getPayment(PAYMENT_KEY)).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            tx.executeWithoutResult(s -> {
                var paid = order();
                assertThat(orders.sumActiveQuantity(ticketId,
                        List.of(OrderStatus.PENDING, OrderStatus.CONFIRMING, OrderStatus.CANCELING),
                        LocalDateTime.now())).isZero();
                assertThat(orders.sumPaidQuantity(ticketId, LocalDateTime.now().minusHours(2),
                        LocalDateTime.now())).isEqualTo(2L);
                var unpaid = Order.createPendingOrder("ORD-UNPAID-CANCEL", paid.getUser(), ticketId,
                        paid.getEvent_id(), 3, 10000, UUID.randomUUID().toString(), 10);
                unpaid.setStatus(OrderStatus.CANCELING);
                unpaid.setExpiresAt(LocalDateTime.now().minusMinutes(1));
                em.persist(unpaid);
                assertThat(orders.sumActiveQuantity(ticketId,
                        List.of(OrderStatus.PENDING, OrderStatus.CONFIRMING, OrderStatus.CANCELING),
                        LocalDateTime.now())).isEqualTo(3L);
            });
            assertThat(orderService.getMyReservations(userId).reservations())
                    .singleElement().satisfies(reservation -> {
                        assertThat(reservation.orderId()).isEqualTo(ORDER_ID);
                        assertThat(reservation.status()).isEqualTo("CANCELING");
                    });
            return pg("CANCELED");
        });

        assertThat(service.cancelReservation(userId, ORDER_ID).status()).isEqualTo("CANCELED");
        assertDb(OrderStatus.CANCELED, "CANCELED", 0);
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void concurrentRequestsCannotRestoreInventoryTwice() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(toss.getPayment(PAYMENT_KEY)).thenReturn(pg("DONE"));
        when(toss.cancel(eq(PAYMENT_KEY), anyString(), anyString())).thenAnswer(i -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return pg("CANCELED");
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> service.cancelReservation(userId, ORDER_ID));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertPending();
                assertDb(OrderStatus.CANCELING, "DONE", 2);
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo("CANCELED");
        }
        assertDb(OrderStatus.CANCELED, "CANCELED", 0);
        verify(toss, times(1)).cancel(anyString(), anyString(), anyString());
    }

    private void failFirstCommitAt(OrderStatus target) {
        var first = new AtomicBoolean(true);
        doAnswer(i -> {
            Object found = mockingDetails(i.getMock()).getMockCreationSettings().getDefaultAnswer().answer(i);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    if (order().getStatus() == target && first.getAndSet(false)) {
                        em.flush();
                        throw new IllegalStateException("injected " + target + " commit failure");
                    }
                }
            });
            return found;
        }).when(orders).findByOrderIdAndUserIdForUpdate(ORDER_ID, userId);
    }

    private void makeRecoveryDue() {
        tx.executeWithoutResult(s -> order().setNextRecoveryAt(LocalDateTime.now().minusSeconds(1)));
    }

    private void assertPending() {
        assertThatThrownBy(() -> service.cancelReservation(userId, ORDER_ID))
                .isInstanceOfSatisfying(ApiException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("RESERVATION_CANCELLATION_PENDING");
                    assertThat(failure.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    private void assertError(Long actorId, String code) {
        assertThatThrownBy(() -> service.cancelReservation(actorId, ORDER_ID))
                .isInstanceOfSatisfying(ApiException.class, failure -> assertThat(failure.getCode()).isEqualTo(code));
    }

    private TossPaymentsClient.TossPaymentResponse pg(String status) {
        return new TossPaymentsClient.TossPaymentResponse(PAYMENT_KEY, ORDER_ID, status, "카드", 20000L,
                null, "test-mid", "KRW", status.equals("CANCELED") ? 0L : 20000L,
                status.equals("CANCELED") ? List.of(new TossPaymentsClient.TossPaymentResponse.Cancel(
                        20000L, "DONE", "2026-10-10T12:00:00+09:00")) : List.of());
    }

    private Order order() {
        return em.createQuery("select o from Order o where o.orderId = :id", Order.class)
                .setParameter("id", ORDER_ID).getSingleResult();
    }

    private void assertDb(OrderStatus status, String paymentStatus, int sold) {
        tx.executeWithoutResult(s -> {
            assertThat(order().getStatus()).isEqualTo(status);
            assertThat(payments.findByOrderOrderId(ORDER_ID).orElseThrow().getStatus()).isEqualTo(paymentStatus);
            assertThat(em.find(Ticket.class, ticketId).getSold_ticket()).isEqualTo(sold);
        });
    }
}
