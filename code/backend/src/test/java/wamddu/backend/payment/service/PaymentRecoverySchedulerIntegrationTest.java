package wamddu.backend.payment.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.order.service.OrderService;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.dto.request.ConfirmPaymentRequest;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.TicketRepository;
import wamddu.backend.user.domain.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_scheduler_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "payment.recovery.enabled=false", "payment.recovery.max-attempts=3"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(30)
class PaymentRecoverySchedulerIntegrationTest {
    @Autowired PaymentService service;
    @Autowired OrderService orderService;
    @MockitoSpyBean OrderRepository orders;
    @Autowired TicketRepository tickets;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean PaymentRepository payments;
    @MockitoBean TossPaymentsClient toss;
    TransactionTemplate tx;
    PaymentRecoveryScheduler scheduler;
    Long userId;
    Long ticketId;
    ConfirmPaymentRequest request;
    String approvalKey;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(manager);
        scheduler = new PaymentRecoveryScheduler(service);
        tx.executeWithoutResult(s -> {
            for (String entity : List.of("Payment", "Order", "Ticket", "Event", "User")) {
                em.createQuery("delete from " + entity).executeUpdate();
            }
            User buyer = User.builder().username("Recovery buyer").password("test-only")
                    .email("scheduler@example.test").customerKey("scheduler_customer").build();
            em.persist(buyer);
            userId = buyer.getId();
            Event event = new Event();
            event.setName("Scheduler test");
            event.setStartDate(LocalDate.now().plusDays(1));
            event.setEndDate(LocalDate.now().plusDays(2));
            event.setLocation("venue");
            event.setBannerImageUrl("banner");
            event.setMainImageUrl("main");
            em.persist(event);
            Ticket ticket = new Ticket();
            ticket.setEvent(event);
            ticket.setType("ticket");
            ticket.setPrice(10000);
            ticket.setTotal_ticket(1);
            ticket.setSold_ticket(0);
            ticket.setBookingEndtime(LocalDateTime.now().plusDays(1));
            em.persist(ticket);
            ticketId = ticket.getId();
        });
        var create = new CreateOrderRequest();
        create.setTicketId(ticketId);
        create.setQuantity(1);
        request = new ConfirmPaymentRequest("scheduler_payment", orderService.createOrder(create, userId).orderId(), 10000L);
        approvalKey = tx.execute(s -> order().getIdempotencyKey());
    }

    @Test
    void newServiceInstanceRecoversUsingOnlyCommittedData() {
        unfinished(OrderStatus.CONFIRMING);
        when(toss.getPayment(request.paymentKey())).thenReturn(response("DONE"));
        var restarted = new PaymentRecoveryScheduler(new PaymentService(orders, payments, tickets, toss, manager));
        restarted.recover();
        restarted.recover();
        assertDb(OrderStatus.PAID, 1, 1);
        tx.executeWithoutResult(s -> assertThat(order().getNextRecoveryAt()).isNull());
        verify(toss, times(1)).confirm(anyString(), anyString(), anyLong(), anyString());
        verify(toss, times(1)).getPayment(request.paymentKey());
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMING", "CANCELING"})
    void providerCancellationIsReflectedWithoutAnotherCancel(String status) {
        unfinished(OrderStatus.valueOf(status));
        when(toss.getPayment(request.paymentKey())).thenReturn(response("CANCELED"));
        scheduler.recover();
        assertDb(OrderStatus.CANCELED, 0, 0);
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @CsvSource({"ABORTED,PAYMENT_FAILED", "EXPIRED,EXPIRED"})
    void providerConfirmedFailureReleasesReservation(String providerStatus, String localStatus) {
        unfinished(OrderStatus.CONFIRMING);
        when(toss.getPayment(request.paymentKey())).thenReturn(response(providerStatus));
        scheduler.recover();
        assertDb(OrderStatus.valueOf(localStatus), 0, 0);
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void canceledIntentResumesWithTheSameCancellationKey() {
        unfinished(OrderStatus.CANCELING);
        when(toss.getPayment(request.paymentKey())).thenReturn(response("DONE"));
        when(toss.cancel(eq(request.paymentKey()), anyString(), eq("cancel:" + approvalKey))).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertDb(OrderStatus.CANCELING, 0, 0);
            return response("CANCELED");
        });
        scheduler.recover();
        assertDb(OrderStatus.CANCELED, 0, 0);
        verify(toss, times(1)).cancel(eq(request.paymentKey()), anyString(), eq("cancel:" + approvalKey));
    }

    @Test
    void lostCancellationResponseIsResolvedByNextLookup() {
        unfinished(OrderStatus.CANCELING);
        when(toss.getPayment(request.paymentKey())).thenReturn(response("DONE"), response("CANCELED"));
        when(toss.cancel(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("response lost"));
        scheduler.recover();
        assertDb(OrderStatus.CANCELING, 0, 0);
        due();
        scheduler.recover();
        assertDb(OrderStatus.CANCELED, 0, 0);
        verify(toss, times(1)).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void lookupFailuresHaveBoundedRetriesAndKeepExpiredReservation() {
        unfinished(OrderStatus.CONFIRMING);
        when(toss.getPayment(request.paymentKey())).thenThrow(new IllegalStateException("lookup unavailable"));
        for (int attempt = 0; attempt < 3; attempt++) {
            due();
            scheduler.recover();
        }
        due();
        scheduler.recover();
        assertDb(OrderStatus.CONFIRMING, 0, 0);
        tx.executeWithoutResult(s -> {
            assertThat(order().isRecoveryReviewRequired()).isTrue();
            assertThat(order().getRecoveryAttempts()).isEqualTo(3);
            order().setExpiresAt(LocalDateTime.now().minusDays(1));
            assertThat(orders.sumActiveQuantity(ticketId, List.of(OrderStatus.CONFIRMING, OrderStatus.CANCELING),
                    LocalDateTime.now())).isEqualTo(1L);
        });
        verify(toss, times(3)).getPayment(request.paymentKey());
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT", "PARTIAL_CANCELED"})
    void nonFinalProviderStatusDoesNotReleaseInventory(String status) {
        unfinished(OrderStatus.CONFIRMING);
        when(toss.getPayment(request.paymentKey())).thenReturn(response(status));
        scheduler.recover();
        assertDb(OrderStatus.CONFIRMING, 0, 0);
        tx.executeWithoutResult(s -> assertThat(order().getNextRecoveryAt()).isAfter(LocalDateTime.now()));
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void mismatchedLookupCannotCompleteOrCancel() {
        unfinished(OrderStatus.CONFIRMING);
        when(toss.getPayment(request.paymentKey())).thenReturn(new TossPaymentsClient.TossPaymentResponse(
                request.paymentKey(), "different_order", "DONE", "카드", 10000L, null));
        scheduler.recover();
        assertDb(OrderStatus.CONFIRMING, 0, 0);
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void recentlyStartedPaymentIsNotSelected() {
        unfinished(OrderStatus.CONFIRMING);
        tx.executeWithoutResult(s -> order().setNextRecoveryAt(LocalDateTime.now().plusMinutes(2)));
        scheduler.recover();
        verify(toss, never()).getPayment(anyString());
    }

    @Test
    void twoWorkersDoNotClaimTheSameDueOrder() throws Exception {
        unfinished(OrderStatus.CONFIRMING);
        var bothSelected = new CyclicBarrier(2);
        doAnswer(i -> {
            Object candidates = mockingDetails(i.getMock()).getMockCreationSettings().getDefaultAnswer().answer(i);
            // 두 서버 모두 같은 후보를 조회한 뒤에 경쟁하도록 만든다.
            bothSelected.await(10, TimeUnit.SECONDS);
            return candidates;
        }).when(orders).findRecoveryCandidates(any(), any());
        when(toss.getPayment(request.paymentKey())).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return response("DONE");
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(scheduler::recover);
            var second = executor.submit(new PaymentRecoveryScheduler(service)::recover);
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertDb(OrderStatus.PAID, 1, 1);
        verify(toss, times(1)).getPayment(request.paymentKey());
    }

    @Test
    void originalApprovalAndRecoveryDoNotDoubleSell() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(toss.confirm(anyString(), anyString(), anyLong(), anyString())).thenAnswer(i -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return response("DONE");
        });
        when(toss.getPayment(request.paymentKey())).thenReturn(response("DONE"));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var original = executor.submit(() -> service.confirm(userId, request));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                due();
                scheduler.recover();
                assertDb(OrderStatus.PAID, 1, 1);
            } finally {
                release.countDown();
            }
            assertThat(original.get(10, TimeUnit.SECONDS).status()).isEqualTo("DONE");
        }
        assertDb(OrderStatus.PAID, 1, 1);
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void schedulerCommitFailureIsRetriedOnALaterRun() {
        unfinished(OrderStatus.CONFIRMING);
        when(toss.getPayment(request.paymentKey())).thenReturn(response("DONE"));
        var first = new AtomicBoolean(true);
        doAnswer(i -> {
            Object saved = mockingDetails(i.getMock()).getMockCreationSettings().getDefaultAnswer().answer(i);
            if (first.getAndSet(false)) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {
                        em.flush();
                        throw new IllegalStateException("scheduler commit failed");
                    }
                });
            }
            return saved;
        }).when(payments).save(any(Payment.class));
        scheduler.recover();
        assertDb(OrderStatus.CONFIRMING, 0, 0);
        due();
        scheduler.recover();
        assertDb(OrderStatus.PAID, 1, 1);
    }

    @Test
    void legacyUnfinishedOrderWithoutPaymentKeyRequiresManualReview() {
        tx.executeWithoutResult(s -> order().setStatus(OrderStatus.CONFIRMING));
        scheduler.recover();
        tx.executeWithoutResult(s -> assertThat(order().isRecoveryReviewRequired()).isTrue());
        verify(toss, never()).getPayment(anyString());
    }

    @Test
    void existingPaymentOnUnfinishedOrderRequiresReviewWithoutProviderCalls() {
        unfinished(OrderStatus.CONFIRMING);
        tx.executeWithoutResult(s -> em.persist(Payment.createPayment(order(), request.paymentKey(),
                10000L, "카드", "DONE", LocalDateTime.now(), null)));
        scheduler.recover();
        assertDb(OrderStatus.CONFIRMING, 1, 0);
        tx.executeWithoutResult(s -> assertThat(order().isRecoveryReviewRequired()).isTrue());
        verify(toss, never()).getPayment(anyString());
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void oneLookupFailureDoesNotBlockTheNextOrder() {
        unfinished(OrderStatus.CONFIRMING);
        tx.executeWithoutResult(s -> em.find(Ticket.class, ticketId).setTotal_ticket(2));
        var create = new CreateOrderRequest();
        create.setTicketId(ticketId);
        create.setQuantity(1);
        String secondId = orderService.createOrder(create, userId).orderId();
        assertThatThrownBy(() -> service.confirm(userId, new ConfirmPaymentRequest("second_payment", secondId, 10000L)))
                .isInstanceOf(RuntimeException.class);
        tx.executeWithoutResult(s -> orders.findByOrderIdAndUserIdForUpdate(secondId, userId).orElseThrow()
                .setNextRecoveryAt(LocalDateTime.now().minusSeconds(1)));
        when(toss.getPayment(request.paymentKey())).thenThrow(new IllegalStateException("first lookup failed"));
        when(toss.getPayment("second_payment")).thenReturn(new TossPaymentsClient.TossPaymentResponse(
                "second_payment", secondId, "DONE", "카드", 10000L, null));
        scheduler.recover();
        tx.executeWithoutResult(s -> {
            assertThat(order().getStatus()).isEqualTo(OrderStatus.CONFIRMING);
            assertThat(orders.findByOrderIdAndUserId(secondId, userId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(em.find(Ticket.class, ticketId).getSold_ticket()).isEqualTo(1);
        });
    }

    @ParameterizedTest
    @CsvSource({"wrong_key,10000", "scheduler_payment,9999"})
    void paidDuplicateRequestStillValidatesKeyAndAmount(String key, long amount) {
        when(toss.confirm(anyString(), anyString(), anyLong(), anyString())).thenReturn(response("DONE"));
        service.confirm(userId, request);
        assertThatThrownBy(() -> service.confirm(userId, new ConfirmPaymentRequest(key, request.orderId(), amount)))
                .isInstanceOf(wamddu.backend.global.exception.ApiException.class)
                .extracting("code").isEqualTo("PAYMENT_STATE_MISMATCH");
        assertDb(OrderStatus.PAID, 1, 1);
        verify(toss, times(1)).confirm(anyString(), anyString(), anyLong(), anyString());
    }

    private void unfinished(OrderStatus status) {
        when(toss.confirm(anyString(), anyString(), anyLong(), anyString())).thenThrow(new IllegalStateException("lost response"));
        assertThatThrownBy(() -> service.confirm(userId, request)).isInstanceOf(RuntimeException.class);
        tx.executeWithoutResult(s -> order().setStatus(status));
        due();
    }

    private void due() {
        tx.executeWithoutResult(s -> order().setNextRecoveryAt(LocalDateTime.now().minusSeconds(1)));
    }

    private TossPaymentsClient.TossPaymentResponse response(String status) {
        return new TossPaymentsClient.TossPaymentResponse(request.paymentKey(), request.orderId(), status, "카드", 10000L, null);
    }

    private Order order() {
        return em.createQuery("select o from Order o where o.orderId = :id", Order.class)
                .setParameter("id", request.orderId()).getSingleResult();
    }

    private void assertDb(OrderStatus status, long payments, int sold) {
        tx.executeWithoutResult(s -> {
            assertThat(order().getStatus()).isEqualTo(status);
            assertThat(em.createQuery("select count(p) from Payment p where p.order.orderId = :id", Long.class)
                    .setParameter("id", request.orderId()).getSingleResult()).isEqualTo(payments);
            assertThat(em.find(Ticket.class, ticketId).getSold_ticket()).isEqualTo(sold);
        });
    }
}
