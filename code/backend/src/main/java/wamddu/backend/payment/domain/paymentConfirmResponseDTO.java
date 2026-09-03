package wamddu.backend.payment.domain;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter
public class paymentConfirmResponseDTO {
    private String orderId;
    private String paymentKey;
    private Integer totalAmount;
    private String method;
    private String status;
    private OffsetDateTime approvedAt;
    private ReceiptDto receipt;

    @Getter
    public static class ReceiptDto {
        private String url;
    }

    public String getReceiptUrl() {
        return (this.receipt != null) ? this.receipt.getUrl() : null;
    }
}
