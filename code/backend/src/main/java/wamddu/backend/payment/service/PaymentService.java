package wamddu.backend.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.repository.orderRepository;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.payment.domain.ConfirmPaymentRequest;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.domain.PaymentResponse;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.ticketRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final orderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ticketRepository ticketRepository;
    private final TossPaymentsClient tossPaymentsClient;

    @Transactional
    public PaymentResponse confirm(Long userId, ConfirmPaymentRequest request) {
        Order order = orderRepository.findByOrderIdAndUserIdForUpdate(request.orderId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));

        if (order.getStatus() == OrderStatus.PAID) {
            return paymentRepository.findByOrderOrderId(order.getOrderId())
                    .map(this::toResponse)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.CONFLICT, "PAYMENT_NOT_FOUND", "결제 기록을 찾을 수 없습니다."));
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
        TossPaymentsClient.TossPaymentResponse toss = tossPaymentsClient.confirm(
                request.paymentKey(), request.orderId(), request.amount(), order.getIdempotencyKey());

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

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentKey(toss.paymentKey());
        payment.setAmount(toss.totalAmount());
        payment.setMethod(toss.method());
        payment.setStatus(toss.status());
        payment.setApprovedAt(approvedAt);
        payment.setReceiptUrl(toss.receipt() == null ? null : toss.receipt().url());
        return toResponse(paymentRepository.save(payment));
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getOrder().getOrderId(),
                payment.getPaymentKey(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getApprovedAt(),
                payment.getReceiptUrl()
        );
    }
}
