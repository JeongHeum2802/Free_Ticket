package wamddu.backend.payment.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class paymentConfirmRequestDTO {

    private String paymentKey;
    private String orderId;
    private Integer amount;
}
