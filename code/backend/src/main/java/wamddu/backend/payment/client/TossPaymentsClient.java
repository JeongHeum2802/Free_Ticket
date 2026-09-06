package wamddu.backend.payment.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;
import wamddu.backend.global.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class TossPaymentsClient {
    private static final Logger log = LoggerFactory.getLogger(TossPaymentsClient.class);

    private final RestClient restClient;
    private final String secretKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            TossErrorResponse tossError = readTossError(exception.getResponseBodyAsString());
            log.error(
                    "Toss payment confirmation failed: status={}, code={}, message={}",
                    exception.getStatusCode().value(),
                    tossError.code(),
                    tossError.message()
            );
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "TOSS_CONFIRM_FAILED",
                    "토스페이먼츠 결제 승인에 실패했습니다. [%s] %s"
                            .formatted(tossError.code(), tossError.message())
            );
        }
    }

    private TossErrorResponse readTossError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new TossErrorResponse("UNKNOWN", "Empty response body");
        }

        try {
            TossErrorResponse response = objectMapper.readValue(responseBody, TossErrorResponse.class);
            return new TossErrorResponse(
                    valueOrDefault(response.code(), "UNKNOWN"),
                    valueOrDefault(response.message(), "No error message")
            );
        } catch (Exception exception) {
            log.warn("Failed to parse Toss Payments error response");
            return new TossErrorResponse("UNKNOWN", "Unparseable response body");
        }
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
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

    private record TossErrorResponse(String code, String message) {
    }
}
