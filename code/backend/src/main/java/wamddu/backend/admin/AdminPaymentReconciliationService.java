package wamddu.backend.admin;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.payment.domain.PaymentReconciliationIssue;
import wamddu.backend.payment.repository.PaymentReconciliationIssueRepository;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.payment.repository.PaymentReconciliationActionRepository;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPaymentReconciliationService {
    private static final Set<String> ISSUE_TYPES = Set.of("PAID_PG_CANCELED", "PAID_PG_PARTIAL_CANCELED",
            "PAYMENT_IDENTITY_MISMATCH", "LOCAL_PAYMENT_INCONSISTENT", "UNMATCHED_PG_CANCELLATION");
    private final PaymentReconciliationIssueRepository issues;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentReconciliationActionRepository actions;
    private final ObjectMapper json = new ObjectMapper();

    public record IssueRow(long id, String orderId, String issueType, String pgStatus,
                           String dbOrderStatus, String dbPaymentStatus, Instant transactionAtUtc, Instant detectedAtUtc,
                           Instant resolvedAtUtc, Long resolvedBy) {
        static IssueRow from(PaymentReconciliationIssue issue) {
            return new IssueRow(issue.getId(), issue.getOrderId(), issue.getIssueType(), issue.getPgStatus(),
                    issue.getDbOrderStatus(), issue.getDbPaymentStatus(),
                    issue.getTransactionAtUtc().toInstant(ZoneOffset.UTC), issue.getDetectedAtUtc().toInstant(ZoneOffset.UTC),
                    issue.getResolvedAtUtc() == null ? null : issue.getResolvedAtUtc().toInstant(ZoneOffset.UTC), issue.getResolvedBy());
        }
    }
    public record IssuePage(List<IssueRow> items, long totalElements, int totalPages, int page, int size) {}
    public record CurrentOrder(String status, int quantity, long totalAmount) {}
    public record CurrentPayment(String status, long amount) {}
    public record CancelRow(long amount, String status, String canceledAt) {}
    public record PgSnapshot(String status, Long totalAmount, Long balanceAmount, List<CancelRow> cancels,
                             boolean identityMatches, boolean canApplyFullCancellation, String blockedReason, Instant checkedAtUtc) {}
    public record ActionRow(long id, long actorId, String action, String result, String reason, String errorCode,
                            Instant occurredAtUtc, PgSnapshot pg, String beforeOrderStatus, String beforePaymentStatus,
                            String afterOrderStatus, String afterPaymentStatus, Integer soldBefore, Integer soldAfter) {}
    public record IssueDetail(IssueRow issue, CurrentOrder currentOrder, CurrentPayment currentPayment,
                              Instant checkedAtUtc, PgSnapshot latestCheck, List<ActionRow> history) {}

    public IssuePage list(String orderId, String issueType, String resolution, OffsetDateTime from, OffsetDateTime to, int page, int size) {
        String orderFilter = normalize(orderId);
        String typeFilter = normalize(issueType);
        String resolutionFilter = normalize(resolution);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || (orderFilter != null && orderFilter.length() > 64)
                || (typeFilter != null && !ISSUE_TYPES.contains(typeFilter))
                || (resolutionFilter != null && !Set.of("OPEN", "RESOLVED").contains(resolutionFilter))
                || (from != null && to != null && !from.toInstant().isBefore(to.toInstant()))) {
            throw invalidQuery();
        }
        var result = issues.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (orderFilter != null) predicates.add(cb.equal(root.get("orderId"), orderFilter));
            if (typeFilter != null) predicates.add(cb.equal(root.get("issueType"), typeFilter));
            if (resolutionFilter != null) predicates.add(resolutionFilter.equals("OPEN")
                    ? cb.isNull(root.get("resolvedAtUtc")) : cb.isNotNull(root.get("resolvedAtUtc")));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("detectedAtUtc"), utc(from)));
            if (to != null) predicates.add(cb.lessThan(root.get("detectedAtUtc"), utc(to)));
            return cb.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "detectedAtUtc", "id")));
        return new IssuePage(result.getContent().stream().map(IssueRow::from).toList(), result.getTotalElements(),
                result.getTotalPages(), result.getNumber(), result.getSize());
    }

    public IssueDetail detail(long id) {
        if (id < 1) throw invalidQuery();
        var issue = issues.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "RECONCILIATION_ISSUE_NOT_FOUND", "불일치 기록을 찾을 수 없습니다."));
        var order = orders.findByOrderId(issue.getOrderId()).orElse(null);
        var payment = order == null ? null : payments.findByOrderOrderId(issue.getOrderId()).orElse(null);
        var history = actions.findTop50ByIssueIdOrderByIdDesc(id).stream().map(a -> new ActionRow(
                a.getId(), a.getActorId(), a.getAction(), a.getResult(), a.getReason(), a.getErrorCode(),
                a.getOccurredAtUtc().toInstant(ZoneOffset.UTC), a.getPgSnapshotJson() == null ? null
                    : json.readValue(a.getPgSnapshotJson(), PgSnapshot.class),
                a.getBeforeOrderStatus(), a.getBeforePaymentStatus(), a.getAfterOrderStatus(), a.getAfterPaymentStatus(),
                a.getSoldBefore(), a.getSoldAfter())).toList();
        return new IssueDetail(IssueRow.from(issue),
                order == null ? null : new CurrentOrder(order.getStatus() == null ? null : order.getStatus().name(),
                        order.getQuantity(), order.getTotalAmount()),
                payment == null ? null : new CurrentPayment(payment.getStatus(), payment.getAmount()), Instant.now(),
                history.stream().map(ActionRow::pg).filter(java.util.Objects::nonNull).findFirst().orElse(null), history);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocalDateTime utc(OffsetDateTime value) {
        return LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }

    private static ApiException invalidQuery() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECONCILIATION_QUERY", "검색 조건과 페이지 범위를 확인해 주세요.");
    }
}
