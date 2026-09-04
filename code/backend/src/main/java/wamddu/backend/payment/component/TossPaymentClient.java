package wamddu.backend.payment.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import wamddu.backend.payment.dto.request.PaymentConfirmRequestDTO;
import wamddu.backend.payment.dto.response.PaymentConfirmResponseDTO;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class TossPaymentClient {

    private final RestClient restClient;
    private final String confirmUrl;
    private final String basicAuthHeader;

    public TossPaymentClient(
            @Value("${TOSS_SECRET_KEY}") String secretKey,
            @Value("${TOSS_CONFIRM_URL}") String confirmUrl
    ) {
        this.confirmUrl = confirmUrl;
        this.restClient = RestClient.create();

        String rawKey = secretKey + ":";
        String encodedKey = Base64.getEncoder().encodeToString(rawKey.getBytes(StandardCharsets.UTF_8));
        this.basicAuthHeader = "Basic " + encodedKey;
    }

    public PaymentConfirmResponseDTO sendConfirmRequest(PaymentConfirmRequestDTO request) {
        return restClient.post()
                .uri(confirmUrl)
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, response) -> {
                    String errorBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new RuntimeException("토스 결제 승인 실패: " + errorBody);
                })
                .body(PaymentConfirmResponseDTO.class);
    }

    public void cancelPayment(String paymentKey, String cancelReason) {
        try {
            restClient.post()
                    .uri("https://api.tosspayments.com/v1/payments/{paymentKey}/cancel", paymentKey)
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cancelReason", cancelReason))
                    .retrieve()
                    .toBodilessEntity();

            log.info("토스 결제 취소 성공: paymentKey={}, 사유={}",  paymentKey, cancelReason);
        } catch (Exception e) {
            log.error("토스 결제 취소 실패! paymentKey={}", paymentKey, e);
        }
    }
}
