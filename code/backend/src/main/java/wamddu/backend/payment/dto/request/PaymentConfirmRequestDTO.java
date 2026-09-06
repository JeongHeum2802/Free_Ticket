package wamddu.backend.payment.dto.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PaymentConfirmRequestDTO {

    private String paymentKey;
    private String orderId;
    private Integer amount;
}
