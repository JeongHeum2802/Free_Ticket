package wamddu.backend.payment.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import wamddu.backend.global.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class TossPaymentsClient {
    private final RestClient restClient;
    private final String secretKey;

    public TossPaymentsClient(
            @Value("${toss.api-base-url}") String apiBaseUrl,
            @Value("${toss.secret-key}") String secretKey
    ) {
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl).build();
        this.secretKey = secretKey;
    }

    public TossPaymentResponse confirm(String paymentKey, String orderId, Long amount, String idempotencyKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "TOSS_SECRET_KEY_NOT_CONFIGURED",
                    "결제 서버 설정이 완료되지 않았습니다."
            );
        }

        String credentials = Base64.getEncoder().encodeToString(
                (secretKey + ":").getBytes(StandardCharsets.UTF_8));
        try {
            return restClient.post()
                    .uri("/v1/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                    .retrieve()
                    .body(TossPaymentResponse.class);
        } catch (RestClientResponseException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "TOSS_CONFIRM_FAILED",
                    "토스페이먼츠 결제 승인에 실패했습니다."
            );
        }
    }

    public record TossPaymentResponse(
            String paymentKey,
            String orderId,
            String status,
            String method,
            Long totalAmount,
            Receipt receipt
    ) {
        public record Receipt(String url) {
        }
    }
}
