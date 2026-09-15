package wamddu.backend.order.dto.response;

import wamddu.backend.event.domain.Event;
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
        Event event = (ticket != null) ? ticket.getEvent() : null;

        Long eventId = (event != null && event.getId() != null)
                ? event.getId()
                : (order.getEvent_id() != null ? order.getEvent_id() : 0L);
        String eventName = (event != null && event.getName() != null)
                ? event.getName()
                : "공연 정보 없음";
        String mainImageUrl = (event != null && event.getMainImageUrl() != null)
                ? event.getMainImageUrl()
                : "";
        String location = (event != null && event.getLocation() != null)
                ? event.getLocation()
                : "장소 미정";
        String ticketType = (ticket != null && ticket.getType() != null)
                ? ticket.getType()
                : "기본 티켓";
        LocalDateTime performanceAt = (ticket != null && ticket.getStart_time() != null)
                ? ticket.getStart_time()
                : order.getOrderDate();
        LocalDateTime paidAt = (order.getPaidAt() != null)
                ? order.getPaidAt()
                : (payment.getApprovedAt() != null ? payment.getApprovedAt() : order.getOrderDate());

        return new ReservationHistoryResponse(
                order.getOrderId(),
                eventId,
                eventName,
                mainImageUrl,
                location,
                ticketType,
                performanceAt,
                order.getQuantity(),
                payment.getAmount() != null ? payment.getAmount() : order.getTotalAmount(),
                paidAt,
                payment.getMethod() != null ? payment.getMethod() : "카드",
                payment.getReceiptUrl(),
                order.getStatus().name()
        );
    }

    public static ReservationHistoryResponse fromOrderOnly(Order order, Ticket ticket) {
        Event event = (ticket != null) ? ticket.getEvent() : null;

        Long eventId = (event != null && event.getId() != null)
                ? event.getId()
                : (order.getEvent_id() != null ? order.getEvent_id() : 0L);
        String eventName = (event != null && event.getName() != null)
                ? event.getName()
                : "공연 정보 없음";
        String mainImageUrl = (event != null && event.getMainImageUrl() != null)
                ? event.getMainImageUrl()
                : "";
        String location = (event != null && event.getLocation() != null)
                ? event.getLocation()
                : "장소 미정";
        String ticketType = (ticket != null && ticket.getType() != null)
                ? ticket.getType()
                : "기본 티켓";
        LocalDateTime performanceAt = (ticket != null && ticket.getStart_time() != null)
                ? ticket.getStart_time()
                : order.getOrderDate();
        LocalDateTime paidAt = (order.getPaidAt() != null)
                ? order.getPaidAt()
                : order.getOrderDate();

        return new ReservationHistoryResponse(
                order.getOrderId(),
                eventId,
                eventName,
                mainImageUrl,
                location,
                ticketType,
                performanceAt,
                order.getQuantity(),
                order.getTotalAmount(),
                paidAt,
                "카드",
                null,
                order.getStatus().name()
        );
    }
}
