package wamddu.backend.order.domain;

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
}
