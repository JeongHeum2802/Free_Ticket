package wamddu.backend.payment.service;

import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import wamddu.backend.event.domain.Event;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.payment.client.TossPaymentsClient.TossTransactionResponse;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.repository.PaymentReconciliationIssueRepository;
import wamddu.backend.payment.repository.PaymentReconciliationProgressRepository;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.user.domain.User;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_reconciliation_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "payment.reconciliation.start-at=2026-01-01T00:00:00+09:00",
        "payment.reconciliation.enabled=true", "payment.reconciliation.initial-delay-ms=600000"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentReconciliationIntegrationTest {
    private static final Instant START = OffsetDateTime.parse("2026-01-01T00:00:00+09:00").toInstant();
    @Autowired PaymentReconciliationService service;
    @Autowired PaymentReconciliationIssueRepository issues;
    @Autowired PaymentReconciliationProgressRepository progress;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired TaskScheduler taskScheduler;
    @Autowired Environment environment;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean TossPaymentsClient toss;
    TransactionTemplate tx;
    String orderId;
    Long ticketId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            for (String entity : List.of("PaymentReconciliationIssue", "PaymentReconciliationProgress",
                    "Payment", "Order", "Ticket", "Event", "User")) {
                em.createQuery("delete from " + entity).executeUpdate();
            }
            User user = User.builder().username("Recon buyer").password("test-only")
                    .email("recon@example.test").customerKey("recon-buyer").build();
            em.persist(user);
            Event event = new Event();
            event.setName("Recon event");
            event.setStartDate(LocalDate.now().plusDays(1));
            event.setEndDate(LocalDate.now().plusDays(2));
            event.setLocation("venue");
            event.setBannerImageUrl("banner");
            event.setMainImageUrl("main");
            em.persist(event);
            Ticket ticket = new Ticket();
            ticket.setEvent(event);
            ticket.setType("general");
            ticket.setPrice(10000);
            ticket.setTotal_ticket(10);
            ticket.setSold_ticket(1);
            ticket.setBookingEndtime(LocalDateTime.now().plusDays(1));
            em.persist(ticket);
            ticketId = ticket.getId();
            orderId = "ORD-RECON-1";
            Order order = Order.builder().orderId(orderId).user(user).ticket_id(ticketId)
                    .event_id(event.getId()).quantity(1).unitPrice(10000).totalAmount(10000L)
                    .status(OrderStatus.PAID).paymentKey("pay-1")
                    .idempotencyKey(UUID.randomUUID().toString()).build();
            em.persist(order);
            em.persist(Payment.createPayment(order, "pay-1", 10000L, "카드", "DONE", LocalDateTime.now(), null));
        });
    }

    @Test
    void cancellationAndTwoPartialTransactionsAreRecordedOnceWithoutChangingBooking() {
        assertThat(service.recordCancellation(transaction("cancel-1", "CANCELED"), START.plusSeconds(100))).isTrue();
        assertThat(service.recordCancellation(transaction("partial-1", "PARTIAL_CANCELED"), START.plusSeconds(100))).isTrue();
        assertThat(service.recordCancellation(transaction("partial-2", "PARTIAL_CANCELED"), START.plusSeconds(100))).isTrue();
        assertThat(service.recordCancellation(transaction("partial-1", "PARTIAL_CANCELED"), START.plusSeconds(200))).isFalse();
        assertThat(issues.findAll()).extracting("issueType").containsExactlyInAnyOrder(
                "PAID_PG_CANCELED", "PAID_PG_PARTIAL_CANCELED", "PAID_PG_PARTIAL_CANCELED");
        tx.executeWithoutResult(status -> {
            Order order = em.createQuery("select o from Order o where o.orderId = :id", Order.class)
                    .setParameter("id", orderId).getSingleResult();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(em.createQuery("select p from Payment p where p.order.id = :id", Payment.class)
                    .setParameter("id", order.getId()).getSingleResult().getStatus()).isEqualTo("DONE");
            assertThat(em.find(Ticket.class, ticketId).getSold_ticket()).isEqualTo(1);
        });
    }

    @Test
    void mismatchedIdentityLocalInconsistencyAndMissingOrderHaveDistinctTypes() {
        assertThat(service.recordCancellation(new TossTransactionResponse("mid", "mismatch", "other",
                orderId, "CANCELED", "2026-01-01T01:00:00+09:00"), START)).isTrue();
        tx.executeWithoutResult(status -> em.createQuery("update Payment p set p.amount = 9999 where p.order.orderId = :id")
                .setParameter("id", orderId).executeUpdate());
        assertThat(service.recordCancellation(transaction("bad-amount", "CANCELED"), START)).isTrue();
        assertThat(service.recordCancellation(new TossTransactionResponse("mid", "missing", "pay-x",
                "ORD-ABSENT", "CANCELED", "2026-01-01T01:00:00+09:00"), START)).isTrue();
        assertThat(issues.findAll()).extracting("issueType").containsExactlyInAnyOrder(
                "PAYMENT_IDENTITY_MISMATCH", "LOCAL_PAYMENT_INCONSISTENT", "UNMATCHED_PG_CANCELLATION");
    }

    @Test
    void otherMerchantOrdersAndUnfinishedLocalOrdersAreSkipped() {
        assertThat(service.recordCancellation(new TossTransactionResponse("mid", "foreign", "pay-x",
                "FOREIGN-1", "CANCELED", "2026-01-01T01:00:00+09:00"), START)).isFalse();
        tx.executeWithoutResult(status -> em.createQuery("update Order o set o.status = :status where o.orderId = :id")
                .setParameter("status", OrderStatus.CONFIRMING).setParameter("id", orderId).executeUpdate());
        assertThat(service.recordCancellation(transaction("unfinished", "CANCELED"), START)).isFalse();
        assertThat(issues.count()).isZero();
    }

    @Test
    void failedSecondPageKeepsProgressAndSuccessfulRetryHandlesExactPageBoundary() {
        List<TossTransactionResponse> fullPage = new ArrayList<>();
        for (int i = 0; i < 500; i++) fullPage.add(new TossTransactionResponse("mid", "done-" + i,
                "pay-" + i, "ORD-OTHER-" + i, "DONE", "2026-01-01T01:00:00+09:00"));
        when(toss.getTransactions(any(), any(), isNull(), eq(500))).thenReturn(fullPage);
        when(toss.getTransactions(any(), any(), eq("done-499"), eq(500)))
                .thenThrow(new IllegalStateException("HTTP 429"))
                .thenReturn(List.of(transaction("cancel-after-page", "CANCELED")));
        Instant now = START.plus(Duration.ofHours(2));
        assertThatThrownBy(() -> service.reconcileUntil(now)).isInstanceOf(IllegalStateException.class);
        assertThat(progress.findById((byte) 1).orElseThrow().getCompletedThroughUtc())
                .isEqualTo(LocalDateTime.ofInstant(START, ZoneOffset.UTC));
        assertThat(issues.count()).isZero();
        var summary = service.reconcileUntil(now);
        assertThat(summary.transactions()).isEqualTo(501);
        assertThat(summary.newIssues()).isEqualTo(1);
        assertThat(progress.findById((byte) 1).orElseThrow().getCompletedThroughUtc())
                .isEqualTo(LocalDateTime.ofInstant(now.minus(Duration.ofMinutes(10)), ZoneOffset.UTC));
        assertThat(issues.count()).isEqualTo(1);
    }

    @Test
    void restartReadsPersistedProgressAndRepeatedWindowDoesNotDuplicateIssue() {
        when(toss.getTransactions(any(), any(), nullable(String.class), eq(500)))
                .thenReturn(List.of(transaction("late-cancel", "CANCELED")));
        Instant firstNow = START.plus(Duration.ofHours(2));
        service.reconcileUntil(firstNow);
        Instant secondNow = firstNow.plus(Duration.ofHours(1));
        new PaymentReconciliationService(toss, orders, payments, issues, progress, manager,
                "2026-01-01T00:00:00+09:00", true).reconcileUntil(secondNow);
        assertThat(issues.count()).isEqualTo(1);
        assertThat(progress.findById((byte) 1).orElseThrow().getCompletedThroughUtc())
                .isEqualTo(LocalDateTime.ofInstant(secondNow.minus(Duration.ofMinutes(10)), ZoneOffset.UTC));
    }

    @Test
    void scheduledTasksHaveTwoWorkerThreads() throws Exception {
        assertThat(environment.getProperty("spring.task.scheduling.pool.size")).isEqualTo("2");
        assertThat(taskScheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
        assertThat(((ThreadPoolTaskScheduler) taskScheduler).getScheduledThreadPoolExecutor().getCorePoolSize())
                .isEqualTo(2);
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        Runnable slowLookup = () -> {
            started.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        };
        try {
            taskScheduler.schedule(slowLookup, Instant.now());
            taskScheduler.schedule(slowLookup, Instant.now());
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
        }
    }

    @Test
    void malformedSecondPageDoesNotAdvanceProgress() {
        List<TossTransactionResponse> fullPage = new ArrayList<>();
        for (int i = 0; i < 500; i++) fullPage.add(new TossTransactionResponse("mid", "done-" + i,
                "pay-" + i, "ORD-OTHER-" + i, "DONE", "2026-01-01T01:00:00+09:00"));
        when(toss.getTransactions(any(), any(), isNull(), eq(500))).thenReturn(fullPage);
        when(toss.getTransactions(any(), any(), eq("done-499"), eq(500)))
                .thenReturn(List.of(new TossTransactionResponse("mid", null, "pay-1", orderId,
                        "CANCELED", "2026-01-01T01:00:00+09:00")));
        assertThatThrownBy(() -> service.reconcileUntil(START.plus(Duration.ofHours(2))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Invalid Toss transaction");
        assertThat(progress.findById((byte) 1).orElseThrow().getCompletedThroughUtc())
                .isEqualTo(LocalDateTime.ofInstant(START, ZoneOffset.UTC));
    }

    @Test
    void enabledReconciliationRequiresAnOffsetStartTime() {
        assertThatThrownBy(() -> new PaymentReconciliationService(toss, orders, payments, issues,
                progress, manager, "", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAYMENT_RECONCILIATION_START_AT");
    }

    @Test
    void backlogAdvancesOnlySevenDaysPerRunAndRecentTenMinutesAreExcluded() {
        when(toss.getTransactions(any(), any(), nullable(String.class), eq(500))).thenReturn(List.of());
        Instant now = START.plus(Duration.ofDays(10));
        var first = service.reconcileUntil(now);
        assertThat(first.windows()).isEqualTo(7);
        assertThat(first.completedThrough()).isEqualTo(START.plus(Duration.ofDays(7)));
        var second = service.reconcileUntil(now);
        assertThat(second.windows()).isEqualTo(3);
        assertThat(second.completedThrough()).isEqualTo(now.minus(Duration.ofMinutes(10)));
        assertThat(issues.count()).isZero();
    }

    @Test
    void sameTransactionKeyFromDifferentMerchantsUsesCompositeIdentity() {
        TossTransactionResponse first = transaction("shared-key", "CANCELED");
        TossTransactionResponse second = new TossTransactionResponse("other-mid", "shared-key", "pay-1",
                orderId, "CANCELED", "2026-01-01T01:00:00+09:00");
        when(toss.getTransactions(any(), any(), isNull(), eq(500))).thenReturn(List.of(first, second));
        var result = service.reconcileUntil(START.plus(Duration.ofHours(2)));
        assertThat(result.newIssues()).isEqualTo(2);
        assertThat(issues.count()).isEqualTo(2);
    }

    @Test
    void databaseUniqueConstraintRejectsDuplicateMerchantTransaction() {
        service.recordCancellation(transaction("unique-key", "CANCELED"), START);
        var first = issues.findAll().getFirst();
        assertThatThrownBy(() -> issues.saveAndFlush(wamddu.backend.payment.domain.PaymentReconciliationIssue.builder()
                .merchantId(first.getMerchantId()).transactionKey(first.getTransactionKey())
                .orderId(orderId).pgPaymentKey("pay-1").issueType("PAID_PG_CANCELED")
                .pgStatus("CANCELED").transactionAtUtc(first.getTransactionAtUtc())
                .detectedAtUtc(first.getDetectedAtUtc()).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private TossTransactionResponse transaction(String key, String status) {
        return new TossTransactionResponse("mid", key, "pay-1", orderId, status, "2026-01-01T01:00:00+09:00");
    }
}
