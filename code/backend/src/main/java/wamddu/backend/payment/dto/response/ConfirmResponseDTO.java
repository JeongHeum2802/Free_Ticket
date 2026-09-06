package wamddu.backend.payment.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
public class ConfirmResponseDTO {
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
