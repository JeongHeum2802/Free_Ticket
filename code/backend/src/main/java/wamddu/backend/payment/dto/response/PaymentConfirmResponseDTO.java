package wamddu.backend.payment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentConfirmResponseDTO {

    private String orderId;
    private String paymentKey;
    private Integer amount;
    private String method;
    private String status;
    private OffsetDateTime approvedAt;
    private String receiptUrl;

    public static PaymentConfirmResponseDTO create(ConfirmResponseDTO response) {
        return PaymentConfirmResponseDTO.builder()
                .orderId(response.getOrderId())
                .paymentKey(response.getPaymentKey())
                .amount(response.getTotalAmount())
                .method(response.getMethod())
                .status(response.getStatus())
                .approvedAt(response.getApprovedAt())
                .receiptUrl(response.getReceiptUrl())
                .build();
    }
}
