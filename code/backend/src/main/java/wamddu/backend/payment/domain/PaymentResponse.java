package wamddu.backend.payment.domain;

import java.time.LocalDateTime;

public record PaymentResponse(
        String orderId,
        String paymentKey,
        Long amount,
        String method,
        String status,
        LocalDateTime approvedAt,
        String receiptUrl
) {
}
