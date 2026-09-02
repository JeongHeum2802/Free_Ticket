package wamddu.backend.payment.domain;

import lombok.Getter;

@Getter
public class paymentConfirmRequestDTO {

    private String paymentKey;
    private String orderId;
    private Integer amount;
}
