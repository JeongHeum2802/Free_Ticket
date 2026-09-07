package wamddu.backend.payment.dto.response;

import wamddu.backend.payment.domain.Payment;

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
    public static PaymentResponse from(Payment payment) {
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
