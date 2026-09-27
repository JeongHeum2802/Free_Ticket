package wamddu.backend.payment.service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.payment.client.TossPaymentsClient.TossTransactionResponse;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.domain.PaymentReconciliationIssue;
import wamddu.backend.payment.domain.PaymentReconciliationProgress;
import wamddu.backend.payment.repository.PaymentReconciliationIssueRepository;
import wamddu.backend.payment.repository.PaymentReconciliationProgressRepository;
import wamddu.backend.payment.repository.PaymentRepository;

@Slf4j
@Service
public class PaymentReconciliationService {
    private static final int PAGE_SIZE = 500;
    private static final ZoneId TOSS_ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneOffset UTC = ZoneOffset.UTC;
    private final TossPaymentsClient toss;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentReconciliationIssueRepository issues;
    private final PaymentReconciliationProgressRepository progressRepository;
    private final TransactionTemplate tx;
    private final Instant configuredStart;

    public PaymentReconciliationService(TossPaymentsClient toss, OrderRepository orders, PaymentRepository payments,
            PaymentReconciliationIssueRepository issues, PaymentReconciliationProgressRepository progressRepository,
            PlatformTransactionManager manager,
            @Value("${payment.reconciliation.start-at:}") String startAt,
            @Value("${payment.reconciliation.enabled:false}") boolean enabled) {
        this.toss = toss;
        this.orders = orders;
        this.payments = payments;
        this.issues = issues;
        this.progressRepository = progressRepository;
        this.tx = new TransactionTemplate(manager);
        if (enabled && (startAt == null || startAt.isBlank())) {
            throw new IllegalArgumentException("PAYMENT_RECONCILIATION_START_AT is required when reconciliation is enabled");
        }
        this.configuredStart = startAt == null || startAt.isBlank() ? null : OffsetDateTime.parse(startAt).toInstant();
    }

    public ReconciliationSummary reconcileUntil(Instant now) {
        if (configuredStart == null) throw new IllegalStateException("Reconciliation start time is not configured");
        PaymentReconciliationProgress progress = tx.execute(status -> progressRepository.findById((byte) 1)
                .orElseGet(() -> progressRepository.save(new PaymentReconciliationProgress(utc(configuredStart)))));
        Instant completed = progress.getCompletedThroughUtc().toInstant(UTC);
        Instant coverage = progress.getCoverageStartUtc().toInstant(UTC);
        // The PG query accepts whole seconds. Persist the same boundary to avoid skipping a fractional second.
        Instant eligible = now.minus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.SECONDS);
        int windows = 0, transactions = 0, newIssues = 0;
        while (windows < 7 && completed.isBefore(eligible)) {
            Instant start = completed.minus(Duration.ofHours(1));
            if (start.isBefore(coverage)) start = coverage;
            Instant end = completed.plus(Duration.ofDays(1));
            if (end.isAfter(eligible)) end = eligible;
            int[] counts = reconcileWindow(start, end, now);
            transactions += counts[0];
            newIssues += counts[1];
            Instant completedWindow = end;
            tx.executeWithoutResult(status -> {
                PaymentReconciliationProgress current = progressRepository.findById((byte) 1).orElseThrow();
                current.setCompletedThroughUtc(utc(completedWindow));
                current.setUpdatedAtUtc(utc(now));
            });
            completed = end;
            windows++;
            log.info("Payment reconciliation window completed: start={}, end={}, transactions={}, newIssues={}",
                    start, end, counts[0], counts[1]);
        }
        return new ReconciliationSummary(windows, transactions, newIssues, completed);
    }

    private int[] reconcileWindow(Instant start, Instant end, Instant detectedAt) {
        String cursor = null;
        Set<TransactionId> seen = new HashSet<>();
        int transactions = 0, newIssues = 0;
        while (true) {
            List<TossTransactionResponse> page = toss.getTransactions(
                    LocalDateTime.ofInstant(start, TOSS_ZONE), LocalDateTime.ofInstant(end, TOSS_ZONE), cursor, PAGE_SIZE);
            if (page == null || page.size() > PAGE_SIZE) throw new IllegalStateException("Invalid transaction page size");
            for (TossTransactionResponse transaction : page) {
                validate(transaction);
                if (!seen.add(new TransactionId(transaction.mId(), transaction.transactionKey()))) {
                    throw new IllegalStateException("Repeated transaction page key");
                }
            }
            newIssues += tx.execute(status -> {
                int added = 0;
                for (TossTransactionResponse transaction : page) {
                    if (recordCancellationInTransaction(transaction, detectedAt)) added++;
                }
                return added;
            });
            transactions += page.size();
            if (page.size() < PAGE_SIZE) break;
            String nextCursor = page.getLast().transactionKey();
            if (nextCursor.equals(cursor)) throw new IllegalStateException("Repeated transaction page cursor");
            cursor = nextCursor;
        }
        return new int[]{transactions, newIssues};
    }

    public boolean recordCancellation(TossTransactionResponse transaction, Instant detectedAt) {
        validate(transaction);
        return tx.execute(status -> recordCancellationInTransaction(transaction, detectedAt));
    }

    private boolean recordCancellationInTransaction(TossTransactionResponse transaction, Instant detectedAt) {
        if (!"CANCELED".equals(transaction.status()) && !"PARTIAL_CANCELED".equals(transaction.status())) return false;
        if (issues.existsByMerchantIdAndTransactionKey(transaction.mId(), transaction.transactionKey())) return false;
        Order order = orders.findByOrderId(transaction.orderId()).orElse(null);
        String issueType;
        Payment payment = null;
        if (order == null) {
            if (!transaction.orderId().startsWith("ORD-")) return false;
            issueType = "UNMATCHED_PG_CANCELLATION";
        } else if (order.getStatus() != OrderStatus.PAID) {
            return false;
        } else {
            payment = payments.findByOrderOrderId(order.getOrderId()).orElse(null);
            if (payment == null || !"DONE".equals(payment.getStatus())
                    || !payment.getAmount().equals(order.getTotalAmount())) {
                issueType = "LOCAL_PAYMENT_INCONSISTENT";
            } else if (!transaction.paymentKey().equals(order.getPaymentKey())
                    || !transaction.paymentKey().equals(payment.getPaymentKey())) {
                issueType = "PAYMENT_IDENTITY_MISMATCH";
            } else {
                issueType = "CANCELED".equals(transaction.status())
                        ? "PAID_PG_CANCELED" : "PAID_PG_PARTIAL_CANCELED";
            }
        }
        issues.save(PaymentReconciliationIssue.builder()
                .merchantId(transaction.mId()).transactionKey(transaction.transactionKey())
                .orderId(transaction.orderId()).pgPaymentKey(transaction.paymentKey())
                .issueType(issueType).pgStatus(transaction.status())
                .dbOrderStatus(order == null ? null : order.getStatus().name())
                .dbPaymentStatus(payment == null ? null : payment.getStatus())
                .transactionAtUtc(utc(OffsetDateTime.parse(transaction.transactionAt()).toInstant()))
                .detectedAtUtc(utc(detectedAt)).build());
        return true;
    }

    private static void validate(TossTransactionResponse t) {
        if (t == null || blank(t.mId()) || blank(t.transactionKey()) || blank(t.paymentKey())
                || blank(t.orderId()) || blank(t.status()) || blank(t.transactionAt())
                || t.mId().length() > 14 || t.transactionKey().length() > 64
                || t.paymentKey().length() > 200 || t.orderId().length() > 64 || t.status().length() > 30) {
            throw new IllegalStateException("Invalid Toss transaction response");
        }
        try {
            OffsetDateTime.parse(t.transactionAt());
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Invalid Toss transaction timestamp", invalid);
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value, UTC); }

    private record TransactionId(String merchantId, String transactionKey) { }

    public record ReconciliationSummary(int windows, int transactions, int newIssues, Instant completedThrough) { }
}
