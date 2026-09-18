package wamddu.backend.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public PaymentResponse confirm(Long userId, ConfirmPaymentRequest request) {
        log.debug("[SQL CHECK] POST /api/payments/confirm START");
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Order preparedOrder = transaction.execute(status -> prepareOrder(userId, request));
            if (preparedOrder.getStatus() == OrderStatus.PAID) {
                return transaction.execute(status -> paymentRepository.findByOrderOrderId(request.orderId())
                        .map(this::toResponse)
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.CONFLICT, "PAYMENT_NOT_FOUND", "결제 기록을 찾을 수 없습니다.")));
            }

            // 첫 트랜잭션의 CONFIRMING 커밋 이후 외부 승인을 요청한다.
            // ponytail: 실패/중단된 CONFIRMING의 재고 해제는 결제 상태 조회 기반 복구 단계에서 추가한다.
            TossPaymentsClient.TossPaymentResponse toss = tossPaymentsClient.confirm(
                    request.paymentKey(), request.orderId(), request.amount(), preparedOrder.getIdempotencyKey());

            return transaction.execute(status -> completePayment(userId, request, toss));
        } finally {
            log.debug("[SQL CHECK] POST /api/payments/confirm END");
        }
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
        return order;
    }

    private PaymentResponse completePayment(Long userId, ConfirmPaymentRequest request,
                                           TossPaymentsClient.TossPaymentResponse toss) {
        Order order = orderRepository.findByOrderIdAndUserIdForUpdate(request.orderId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        if (order.getStatus() != OrderStatus.CONFIRMING) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS", "결제 처리 중인 주문이 아닙니다.");
        }
        try {
            if (toss == null || !order.getOrderId().equals(toss.orderId())
                    || !order.getTotalAmount().equals(toss.totalAmount()) || !"DONE".equals(toss.status())) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY, "INVALID_TOSS_RESPONSE", "결제 승인 결과가 주문 정보와 일치하지 않습니다.");
            }

            Ticket ticket = ticketRepository.findByIdForUpdate(order.getTicket_id())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "티켓을 찾을 수 없습니다."));
            int soldTicket = ticket.getSold_ticket() == null ? 0 : ticket.getSold_ticket();
            int nextSoldTicket = soldTicket + order.getQuantity();
            if (ticket.getTotal_ticket() == null || nextSoldTicket > ticket.getTotal_ticket()) {
                throw new ApiException(HttpStatus.CONFLICT, "TICKET_SOLD_OUT", "남은 티켓 수량이 부족합니다.");
            }
            ticket.setSold_ticket(nextSoldTicket);

            LocalDateTime approvedAt = LocalDateTime.now();
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(approvedAt);

            Payment payment = Payment.createPayment(
                    order,
                    toss.paymentKey(),
                    toss.totalAmount(),
                    toss.method(),
                    toss.status(),
                    approvedAt,
                    toss.receipt() == null ? null : toss.receipt().url()
            );
            return toResponse(paymentRepository.save(payment));
        } catch (Exception e) {
            tossPaymentsClient.cancel(request.paymentKey(), "Error", order.getIdempotencyKey());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", e.getMessage());
        }
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.from(payment);
    }
}
