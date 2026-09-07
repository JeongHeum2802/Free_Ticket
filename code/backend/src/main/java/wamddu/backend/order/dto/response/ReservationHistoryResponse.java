package wamddu.backend.order.dto.response;

import wamddu.backend.order.domain.Order;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.ticket.domain.Ticket;

import java.time.LocalDateTime;

public record ReservationHistoryResponse(
        String orderId,
        Long eventId,
        String eventName,
        String mainImageUrl,
        String location,
        String ticketType,
        LocalDateTime performanceAt,
        Integer quantity,
        Long amount,
        LocalDateTime paidAt,
        String paymentMethod,
        String receiptUrl,
        String status
) {
    public static ReservationHistoryResponse of(Payment payment, Ticket ticket) {
        Order order = payment.getOrder();
        return new ReservationHistoryResponse(
                order.getOrderId(),
                ticket.getEvent().getId(),
                ticket.getEvent().getName(),
                ticket.getEvent().getMainImageUrl(),
                ticket.getEvent().getLocation(),
                ticket.getType(),
                ticket.getStart_time(),
                order.getQuantity(),
                payment.getAmount(),
                order.getPaidAt(),
                payment.getMethod(),
                payment.getReceiptUrl(),
                order.getStatus().name()
        );
    }
}
