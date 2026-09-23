package wamddu.backend.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.dto.request.ConfirmPaymentRequest;
import wamddu.backend.payment.dto.response.PaymentResponse;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.TicketRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final PlatformTransactionManager transactionManager;

    @Value("${payment.recovery.retry-delay-ms:120000}")
    private long recoveryRetryDelayMs = 120000;

    @Value("${payment.recovery.max-attempts:20}")
    private int recoveryMaxAttempts = 20;

    public PaymentResponse confirm(Long userId, ConfirmPaymentRequest request) {
        log.debug("[SQL CHECK] POST /api/payments/confirm START");
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            //트랜잭션 A
            Order preparedOrder = transaction.execute(status -> prepareOrder(userId, request));

            if (preparedOrder.getStatus() == OrderStatus.PAID) {
                return transaction.execute(status -> matchingPaidResponse(preparedOrder, request,
                        paymentRepository.findByOrderOrderId(request.orderId()).orElse(null)));
            }

            //토스 외부 승인 요청
            TossPaymentsClient.TossPaymentResponse toss = tossPaymentsClient.confirm(
                    request.paymentKey(), request.orderId(), request.amount(), preparedOrder.getIdempotencyKey());

            //트랜잭션 B
            try {
                return transaction.execute(status -> completePayment(userId, request, toss));
            } catch (RuntimeException failure) {
                log.warn("결제 완료 처리 실패. DB 상태를 확인합니다. orderId={}", request.orderId(), failure);
                return recoverPayment(userId, request, toss, transaction, failure);
            }
        } finally {
            log.debug("[SQL CHECK] POST /api/payments/confirm END");
        }
    }

    private PaymentResponse recoverPayment(
            Long userId,
            ConfirmPaymentRequest request,
            TossPaymentsClient.TossPaymentResponse toss,
            TransactionTemplate transaction,
            RuntimeException failure
    ) {
        RecoveryResult result;
        try {
            result = transaction.execute(status -> {
                Order order = orderRepository.findByOrderIdAndUserIdForUpdate(request.orderId(), userId)
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
                var payment = paymentRepository.findByOrderOrderId(request.orderId());

                //이미 정상적으로 반영되있음 -> 저장된 결과 반환
                if (order.getStatus() == OrderStatus.PAID) {
                    PaymentResponse response = matchingPaidResponse(order, request, payment.orElse(null));
                    return new RecoveryResult(response, null);
                }

                if (order.getStatus() == OrderStatus.CANCELING && payment.isEmpty()) {
                    validateApproval(order, request, toss);
                    return new RecoveryResult(null, "cancel:" + order.getIdempotencyKey());
                }

                //내부 완료 처리가 반영 X -> 토스 승인 결과를 기준으로 복구
                if (order.getStatus() == OrderStatus.CONFIRMING && payment.isEmpty()) {
                    validateApproval(order, request, toss);
                    Ticket ticket = ticketRepository.findByIdForUpdate(order.getTicket_id()).orElse(null);
                    if (!canFulfill(order, ticket)) {
                        // 취소 의도를 먼저 커밋해야 완료 재시도와 외부 취소가 겹치지 않는다.
                        order.setStatus(OrderStatus.CANCELING);
                        order.setNextRecoveryAt(nextRecoveryAt());
                        return new RecoveryResult(null, "cancel:" + order.getIdempotencyKey());
                    }
                    return new RecoveryResult(applyPayment(order, ticket, toss), null);
                }

                throw recoveryPending(failure);
            });
        } catch (RuntimeException retryFailure) {
            log.warn("결제 복구 미완료. 후속 확인 필요. orderId={}", request.orderId(), retryFailure);
            throw recoveryPending(retryFailure);
        }
        if (result.payment() != null) {
            return result.payment();
        }

        // CANCELING 커밋이 성공한 경우에만 DB 트랜잭션 밖에서 취소한다.
        try {
            var canceled = tossPaymentsClient.cancel(request.paymentKey(), "티켓 제공 불가", result.cancelKey());
            if (canceled == null || !request.paymentKey().equals(canceled.paymentKey())
                    || !request.orderId().equals(canceled.orderId())
                    || !request.amount().equals(canceled.totalAmount()) || !"CANCELED".equals(canceled.status())) {
                throw recoveryPending(failure);
            }
            transaction.executeWithoutResult(status -> finishUnpaidOrder(userId, request, OrderStatus.CANCELED));
        } catch (RuntimeException cancelFailure) {
            log.warn("결제 취소 결과 확정 실패. 후속 확인 필요. orderId={}", request.orderId(), cancelFailure);
            throw recoveryPending(cancelFailure);
        }
        throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_CANCELED", "티켓을 제공할 수 없어 결제를 취소했습니다.");
    }

    private record RecoveryResult(PaymentResponse payment, String cancelKey) {}

    private ApiException recoveryPending(RuntimeException cause) {
        ApiException pending = new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PAYMENT_RECOVERY_PENDING",
                "결제 결과를 확정하지 못했습니다. 다시 결제하지 말고 주문번호로 문의해 주세요."
        );
        pending.initCause(cause);
        return pending;
    }

    private Order prepareOrder(Long userId, ConfirmPaymentRequest request) {
        Order order = orderRepository.findByOrderIdAndUserIdForUpdate(request.orderId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));

        if (order.getStatus() == OrderStatus.PAID) {
            return order;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "INVALID_ORDER_STATUS", "결제를 승인할 수 없는 주문 상태입니다.");
        }
        if (LocalDateTime.now().isAfter(order.getExpiresAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_EXPIRED", "결제 가능 시간이 만료되었습니다.");
        }
        if (!order.getTotalAmount().equals(request.amount())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH", "결제 금액이 주문 금액과 일치하지 않습니다.");
        }
        if (paymentRepository.existsByPaymentKey(request.paymentKey())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_KEY_ALREADY_USED", "이미 사용된 결제 키입니다.");
        }

        order.setStatus(OrderStatus.CONFIRMING);
        order.setPaymentKey(request.paymentKey());
        order.setNextRecoveryAt(nextRecoveryAt());
        return order;
    }

    private PaymentResponse completePayment(Long userId, ConfirmPaymentRequest request,
                                           TossPaymentsClient.TossPaymentResponse toss) {
        Order order = orderRepository.findByOrderIdAndUserIdForUpdate(request.orderId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        if (order.getStatus() != OrderStatus.CONFIRMING) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS", "결제 처리 중인 주문이 아닙니다.");
        }
        validateApproval(order, request, toss);
        Ticket ticket = ticketRepository.findByIdForUpdate(order.getTicket_id()).orElse(null);
        if (!canFulfill(order, ticket)) {
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_UNAVAILABLE", "티켓을 제공할 수 없습니다.");
        }
        return applyPayment(order, ticket, toss);
    }

    private void validateApproval(Order order, ConfirmPaymentRequest request,
                                  TossPaymentsClient.TossPaymentResponse toss) {
        validatePaymentIdentity(request, toss);
        if (!request.paymentKey().equals(order.getPaymentKey())
                || !request.amount().equals(order.getTotalAmount())
                || !"DONE".equals(toss.status())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_TOSS_RESPONSE", "결제 승인 결과가 주문 정보와 일치하지 않습니다.");
        }
    }

    private void validatePaymentIdentity(ConfirmPaymentRequest request, TossPaymentsClient.TossPaymentResponse toss) {
        if (toss == null || !request.paymentKey().equals(toss.paymentKey())
                || !request.orderId().equals(toss.orderId()) || !request.amount().equals(toss.totalAmount())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_TOSS_RESPONSE", "결제 조회 결과가 주문 정보와 일치하지 않습니다.");
        }
    }

    private boolean canFulfill(Order order, Ticket ticket) {
        if (ticket == null) {
            return false;
        }
        if (order.getQuantity() == null || order.getQuantity() <= 0
                || ticket.getTotal_ticket() == null || ticket.getTotal_ticket() < 0
                || ticket.getSold_ticket() == null || ticket.getSold_ticket() < 0
                || ticket.getSold_ticket() > ticket.getTotal_ticket()) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TICKET_DATA", "티켓 판매 정보가 올바르지 않습니다.");
        }
        return (long) ticket.getSold_ticket() + order.getQuantity() <= ticket.getTotal_ticket();
    }

    private PaymentResponse applyPayment(Order order, Ticket ticket, TossPaymentsClient.TossPaymentResponse toss) {
        ticket.setSold_ticket(ticket.getSold_ticket() + order.getQuantity());
        LocalDateTime approvedAt = LocalDateTime.now();
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(approvedAt);
        order.setNextRecoveryAt(null);
        order.setRecoveryReviewRequired(false);
        Payment payment = Payment.createPayment(
                order, toss.paymentKey(), toss.totalAmount(), toss.method(), toss.status(), approvedAt,
                toss.receipt() == null ? null : toss.receipt().url());
        return toResponse(paymentRepository.save(payment));
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.from(payment);
    }

    public void recoverPendingPayments() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // ponytail: 순차 배치 20건. 처리량이 부족해지면 잠금·멱등성을 유지하는 작업자 병렬화를 검토한다.
        var candidates = orderRepository.findRecoveryCandidates(LocalDateTime.now(), PageRequest.of(0, 20));
        for (Long id : candidates) {
            RecoveryAttempt attempt = null;
            try {
                attempt = transaction.execute(status -> claimRecovery(id));
                if (attempt == null) {
                    continue;
                }
                var toss = tossPaymentsClient.getPayment(attempt.request().paymentKey());
                validatePaymentIdentity(attempt.request(), toss);
                final RecoveryAttempt claimed = attempt;
                if ("DONE".equals(toss.status())) {
                    recoverPayment(attempt.userId(), attempt.request(), toss, transaction,
                            new IllegalStateException("Unfinished payment recovery"));
                } else if ("CANCELED".equals(toss.status())) {
                    transaction.executeWithoutResult(status -> finishUnpaidOrder(
                            claimed.userId(), claimed.request(), OrderStatus.CANCELED));
                } else if ("ABORTED".equals(toss.status()) || "EXPIRED".equals(toss.status())) {
                    OrderStatus terminal = "ABORTED".equals(toss.status()) ? OrderStatus.PAYMENT_FAILED : OrderStatus.EXPIRED;
                    transaction.executeWithoutResult(status -> finishUnpaidOrder(claimed.userId(), claimed.request(), terminal));
                } else {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                            "PAYMENT_STATUS_UNRESOLVED", "결제 상태가 확정되지 않았습니다.");
                }
            } catch (RuntimeException failure) {
                if (failure instanceof ApiException api && "PAYMENT_CANCELED".equals(api.getCode())) {
                    continue; // 취소 API와 DB 반영까지 완료한 결과다.
                }
                log.warn("예약된 결제 복구 실패. orderPk={}, orderId={}, attempt={}", id,
                        attempt == null ? null : attempt.request().orderId(),
                        attempt == null ? null : attempt.number(), failure);
                if (attempt != null) {
                    recordRecoveryFailure(id, attempt.number(), transaction);
                }
            }
        }
    }

    private RecoveryAttempt claimRecovery(Long id) {
        Order order = orderRepository.findByIdForUpdate(id).orElse(null);
        if (order == null || !isUnfinished(order) || order.isRecoveryReviewRequired()
                || (order.getNextRecoveryAt() != null && order.getNextRecoveryAt().isAfter(LocalDateTime.now()))) {
            return null;
        }
        if (order.getPaymentKey() == null || order.getPaymentKey().isBlank()
                || order.getRecoveryAttempts() >= recoveryMaxAttempts
                || paymentRepository.findByOrderOrderId(order.getOrderId()).isPresent()) {
            order.setRecoveryReviewRequired(true);
            order.setNextRecoveryAt(null);
            log.error("결제 수동 확인 필요. orderId={}", order.getOrderId());
            return null;
        }
        order.setRecoveryAttempts(order.getRecoveryAttempts() + 1);
        // 외부 호출 전에 커밋하여 다른 서버가 같은 주문을 즉시 가져가지 못하게 한다.
        order.setNextRecoveryAt(nextRecoveryAt());
        return new RecoveryAttempt(order.getUser().getId(), new ConfirmPaymentRequest(
                order.getPaymentKey(), order.getOrderId(), order.getTotalAmount()), order.getRecoveryAttempts());
    }

    private void recordRecoveryFailure(Long id, int attempt, TransactionTemplate transaction) {
        try {
            transaction.executeWithoutResult(status -> {
                Order order = orderRepository.findByIdForUpdate(id).orElse(null);
                if (order != null && isUnfinished(order) && order.getRecoveryAttempts() == attempt
                        && attempt >= recoveryMaxAttempts) {
                    order.setRecoveryReviewRequired(true);
                    order.setNextRecoveryAt(null);
                    log.error("결제 자동 복구 한도 초과. 수동 확인 필요. orderId={}", order.getOrderId());
                }
            });
        } catch (RuntimeException failure) {
            log.error("복구 실패 기록 저장 실패. 다음 주기에 DB 상태를 다시 확인합니다. orderId={}", id, failure);
        }
    }

    private void finishUnpaidOrder(Long userId, ConfirmPaymentRequest request, OrderStatus terminal) {
        Order order = orderRepository.findByOrderIdAndUserIdForUpdate(request.orderId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        if (!request.paymentKey().equals(order.getPaymentKey()) || !request.amount().equals(order.getTotalAmount())
                || paymentRepository.findByOrderOrderId(request.orderId()).isPresent()
                || (order.getStatus() != terminal && !isUnfinished(order))
                || (order.getStatus() == OrderStatus.CANCELING && terminal != OrderStatus.CANCELED)) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_STATE_MISMATCH", "결제 상태를 확정할 수 없습니다.");
        }
        order.setStatus(terminal);
        order.setNextRecoveryAt(null);
        order.setRecoveryReviewRequired(false);
    }

    private boolean isUnfinished(Order order) {
        return order.getStatus() == OrderStatus.CONFIRMING || order.getStatus() == OrderStatus.CANCELING;
    }

    private LocalDateTime nextRecoveryAt() {
        return LocalDateTime.now().plusNanos(recoveryRetryDelayMs * 1_000_000);
    }

    private record RecoveryAttempt(Long userId, ConfirmPaymentRequest request, int number) {}

    private PaymentResponse matchingPaidResponse(Order order, ConfirmPaymentRequest request, Payment payment) {
        if (payment == null || !request.paymentKey().equals(payment.getPaymentKey())
                || !request.amount().equals(payment.getAmount()) || !request.amount().equals(order.getTotalAmount())
                || !"DONE".equals(payment.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_STATE_MISMATCH", "저장된 결제 정보가 요청과 일치하지 않습니다.");
        }
        return toResponse(payment);
    }
}
