package wamddu.backend.admin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.domain.PaymentReconciliationAction;
import wamddu.backend.payment.domain.PaymentReconciliationIssue;
import wamddu.backend.payment.repository.*;
import wamddu.backend.ticket.repository.TicketRepository;
import static wamddu.backend.admin.AdminPaymentReconciliationService.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPaymentReconciliationActions {
    private final PaymentReconciliationIssueRepository issues;
    private final PaymentReconciliationActionRepository actions;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final TicketRepository tickets;
    private final TossPaymentsClient toss;
    private final PlatformTransactionManager manager;
    private final AdminPaymentReconciliationService queries;
    private final ObjectMapper json = new ObjectMapper();
    private record ObservedState(String order, String payment, Integer sold) {}

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public IssueDetail perform(long id, long actorId, String operation, String reason) {
        String action = switch (operation) {
            case "recheck" -> "RECHECK";
            case "apply-full-cancellation" -> "APPLY_FULL_CANCELLATION";
            case "notes" -> "NOTE";
            default -> throw new ApiException(HttpStatus.NOT_FOUND, "UNKNOWN_RECONCILIATION_ACTION", "처리 작업을 찾을 수 없습니다.");
        };
        if (id < 1 || reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECONCILIATION_ACTION", "처리 사유를 1~500자로 입력해 주세요.");
        }
        var tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var reference = tx.execute(s -> issues.findReference(id).orElseThrow(() -> notFound()));
        var evidence = new AtomicReference<PgSnapshot>();
        var observed = new AtomicReference<ObservedState>();
        try {
            // 외부 조회 중에는 주문·결제·재고 잠금과 DB 트랜잭션을 잡지 않는다.
            var pg = action.equals("NOTE") ? null : toss.getPayment(reference.getPgPaymentKey());
            var checkedAt = Instant.now();
            if (pg != null) evidence.set(snapshot(pg, false, "내부 정보 확인 전", checkedAt));
            var received = pg;
            tx.executeWithoutResult(s -> {
                var order = action.equals("APPLY_FULL_CANCELLATION")
                        ? orders.findByOrderIdForUpdate(reference.getOrderId()).orElse(null)
                        : orders.findByOrderId(reference.getOrderId()).orElse(null);
                // 같은 주문의 다른 불일치도 주문 잠금 뒤에 처리하여 재고를 한 번만 복구한다.
                var issue = action.equals("APPLY_FULL_CANCELLATION")
                        ? issues.findByIdForUpdate(id).orElseThrow(() -> notFound())
                        : issues.findById(id).orElseThrow(() -> notFound());
                var payment = order == null ? null : action.equals("APPLY_FULL_CANCELLATION")
                        ? payments.findByOrderOrderIdForUpdate(issue.getOrderId()).orElse(null)
                        : payments.findByOrderOrderId(issue.getOrderId()).orElse(null);
                String beforeOrder = orderStatus(order), beforePayment = payment == null ? null : payment.getStatus();
                observed.set(new ObservedState(beforeOrder, beforePayment, null));
                var checked = received == null ? null : inspect(issue, order, payment, received, checkedAt);
                evidence.set(checked);
                Integer soldBefore = null, soldAfter = null;
                String result = "SUCCESS";
                if (action.equals("APPLY_FULL_CANCELLATION")) {
                    if (checked == null || !checked.canApplyFullCancellation()) {
                        throw new ApiException(HttpStatus.CONFLICT, "RECONCILIATION_BLOCKED",
                                checked == null ? "토스 조회 결과를 확인할 수 없습니다." : checked.blockedReason());
                    }
                    if (order.getStatus() == OrderStatus.CANCELED) {
                        result = "NO_CHANGE";
                    } else {
                        var ticket = order.getTicket_id() == null ? null : tickets.findByIdForUpdate(order.getTicket_id()).orElse(null);
                        if (ticket == null || ticket.getEvent() == null || !Objects.equals(ticket.getEvent().getId(), order.getEvent_id())
                                || order.getQuantity() == null || order.getQuantity() <= 0
                                || ticket.getSold_ticket() == null || ticket.getTotal_ticket() == null
                                || ticket.getSold_ticket() < order.getQuantity() || ticket.getSold_ticket() > ticket.getTotal_ticket()) {
                            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TICKET_DATA", "판매 수량 또는 티켓 정보를 확인해야 합니다. 재고를 변경하지 않았습니다.");
                        }
                        soldBefore = ticket.getSold_ticket();
                        observed.set(new ObservedState(beforeOrder, beforePayment, soldBefore));
                        soldAfter = soldBefore - order.getQuantity();
                        ticket.setSold_ticket(soldAfter);
                        order.setStatus(OrderStatus.CANCELED);
                        order.setNextRecoveryAt(null);
                        order.setRecoveryReviewRequired(false);
                        payment.setStatus("CANCELED");
                    }
                    issue.resolve(LocalDateTime.ofInstant(checkedAt, ZoneOffset.UTC), actorId);
                }
                actions.save(PaymentReconciliationAction.builder().issue(issue).actorId(actorId).action(action)
                        .result(result).reason(reason.trim()).occurredAtUtc(LocalDateTime.now(ZoneOffset.UTC))
                        .pgSnapshotJson(checked == null ? null : json.writeValueAsString(checked))
                        .beforeOrderStatus(beforeOrder).beforePaymentStatus(beforePayment)
                        .afterOrderStatus(orderStatus(order)).afterPaymentStatus(payment == null ? null : payment.getStatus())
                        .soldBefore(soldBefore).soldAfter(soldAfter).build());
            });
        } catch (RuntimeException failure) {
            String code = failure instanceof ApiException api ? api.getCode() : "RECONCILIATION_PROCESSING_FAILED";
            var failedEvidence = evidence.get();
            var before = observed.get();
            // 정상화 트랜잭션이 롤백된 뒤 실패 이력만 별도 커밋한다.
            try {
                tx.executeWithoutResult(s -> actions.save(PaymentReconciliationAction.builder()
                        .issue(issues.findById(id).orElseThrow(() -> notFound())).actorId(actorId).action(action)
                        .result("FAILED").reason(reason.trim()).errorCode(code).occurredAtUtc(LocalDateTime.now(ZoneOffset.UTC))
                        .pgSnapshotJson(failedEvidence == null ? null : json.writeValueAsString(failedEvidence))
                        .beforeOrderStatus(before == null ? null : before.order()).afterOrderStatus(before == null ? null : before.order())
                        .beforePaymentStatus(before == null ? null : before.payment()).afterPaymentStatus(before == null ? null : before.payment())
                        .soldBefore(before == null ? null : before.sold()).soldAfter(before == null ? null : before.sold()).build()));
            } catch (RuntimeException auditFailure) {
                log.error("관리자 결제 처리 실패 이력 저장 실패. issueId={}, actorId={}, code={}", id, actorId, code);
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RECONCILIATION_AUDIT_FAILED", "처리 결과와 이력을 저장하지 못했습니다. 다시 조회해 주세요.");
            }
            if (failure instanceof ApiException api) throw api;
            log.error("관리자 결제 처리 저장 실패. issueId={}, actorId={}", id, actorId);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, "처리를 저장하지 못했습니다. 다시 조회한 뒤 재시도해 주세요.");
        }
        return queries.detail(id);
    }

    private PgSnapshot inspect(PaymentReconciliationIssue issue, Order order, Payment payment,
                               TossPaymentsClient.TossPaymentResponse pg, Instant checkedAt) {
        boolean identity = pg != null && issue.getPgPaymentKey().equals(pg.paymentKey())
                && issue.getOrderId().equals(pg.orderId()) && issue.getMerchantId().equals(pg.mId())
                && "KRW".equals(pg.currency()) && order != null && payment != null
                && issue.getPgPaymentKey().equals(order.getPaymentKey()) && issue.getPgPaymentKey().equals(payment.getPaymentKey())
                && pg.totalAmount() != null && pg.totalAmount() > 0
                && pg.totalAmount().equals(order.getTotalAmount()) && pg.totalAmount().equals(payment.getAmount())
                && order.getQuantity() != null && order.getQuantity() > 0 && order.getUnitPrice() != null
                && (long) order.getQuantity() * order.getUnitPrice() == order.getTotalAmount();
        String blocked = null;
        if (!identity) blocked = "주문·결제·상점 식별 정보 또는 금액이 일치하지 않습니다. 조사 사유를 기록해 주세요.";
        else if (!"CANCELED".equals(pg.status()) || pg.balanceAmount() == null || pg.balanceAmount() != 0
                || !completedFullCancellation(pg)) blocked = "완료된 전액 취소만 반영할 수 있습니다. 부분 취소는 별도 조사가 필요합니다.";
        else if (order.getStatus() == OrderStatus.CANCELED && "CANCELED".equals(payment.getStatus())) {
            if (!actions.hasAppliedCancellation(issue.getOrderId(), issue.getPgPaymentKey(), issue.getMerchantId()))
                blocked = "내부 취소의 재고 복구 이력을 확인할 수 없습니다. 수동 조사가 필요합니다.";
        } else if (order.getStatus() != OrderStatus.PAID || !"DONE".equals(payment.getStatus()))
            blocked = "내부 주문과 결제가 정상적인 결제 완료 상태가 아닙니다. 수동 조사가 필요합니다.";
        else if (actions.hasAppliedCancellation(issue.getOrderId(), issue.getPgPaymentKey(), issue.getMerchantId()))
            blocked = "이미 재고를 복구한 결제입니다. 현재 내부 상태에 대한 수동 조사가 필요합니다.";
        return snapshot(pg, identity, blocked, checkedAt);
    }

    private boolean completedFullCancellation(TossPaymentsClient.TossPaymentResponse pg) {
        if (pg.cancels() == null || pg.cancels().isEmpty()) return false;
        long sum = 0;
        for (var cancel : pg.cancels()) {
            if (cancel == null || cancel.cancelAmount() == null || cancel.cancelAmount() <= 0
                    || !"DONE".equals(cancel.cancelStatus())) return false;
            try { sum = Math.addExact(sum, cancel.cancelAmount()); }
            catch (ArithmeticException overflow) { return false; }
        }
        return Objects.equals(sum, pg.totalAmount());
    }

    private PgSnapshot snapshot(TossPaymentsClient.TossPaymentResponse pg, boolean identity, String blocked, Instant at) {
        var cancels = pg.cancels() == null ? List.<CancelRow>of() : pg.cancels().stream().filter(Objects::nonNull)
                .map(c -> new CancelRow(c.cancelAmount() == null ? 0 : c.cancelAmount(), c.cancelStatus(), cancelTime(c.canceledAt()))).toList();
        return new PgSnapshot(pg.status(), pg.totalAmount(), pg.balanceAmount(), cancels, identity,
                identity && blocked == null, blocked, at);
    }

    private String cancelTime(String value) {
        try {
            if (value != null) return OffsetDateTime.parse(value).toInstant().toString();
        } catch (java.time.format.DateTimeParseException invalid) {
            // 잘못된 외부 시각을 화면 또는 저장된 확인 근거에 넘기지 않는다.
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_TOSS_LOOKUP_RESPONSE", "토스 취소 시각을 확인할 수 없습니다.");
    }

    private static String orderStatus(Order order) {
        return order == null || order.getStatus() == null ? null : order.getStatus().name();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RECONCILIATION_ISSUE_NOT_FOUND", "불일치 기록을 찾을 수 없습니다.");
    }
}
