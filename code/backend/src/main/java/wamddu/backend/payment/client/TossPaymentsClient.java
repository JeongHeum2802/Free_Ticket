package wamddu.backend.payment.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;
import wamddu.backend.global.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl).requestFactory(factory).build();
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

    public TossPaymentResponse getPayment(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank() || paymentKey.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_KEY", "결제 키를 확인해 주세요.");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "TOSS_SECRET_KEY_NOT_CONFIGURED", "결제 서버 설정이 완료되지 않았습니다.");
        }
        String credentials = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        try {
            var response = restClient.get().uri("/v1/payments/{paymentKey}", paymentKey)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                    .retrieve().body(TossPaymentResponse.class);
            if (response == null || !paymentKey.equals(response.paymentKey())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "INVALID_TOSS_LOOKUP_RESPONSE", "결제 조회 결과를 확인할 수 없습니다.");
            }
            return response;
        } catch (RestClientResponseException exception) {
            // 404도 미승인 확정으로 해석하지 않는다. 원래 승인 요청이 진행 중일 수 있다.
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TOSS_LOOKUP_FAILED", "토스 결제 상태 조회에 실패했습니다.");
        }
    }

    /**
     * 전액 취소 요청. 같은 취소 작업의 재시도에는 같은 키를 사용하고,
     * 승인 요청에 사용한 멱등성 키와는 구분해야 한다.
     * 입금된 가상계좌의 환불 계좌 전달은 별도 지원이 필요하다.
     */
    public TossPaymentResponse cancel(String paymentKey, String cancelReason, String idempotencyKey) {
        if (paymentKey == null || paymentKey.isBlank() || paymentKey.length() > 200
                || cancelReason == null || cancelReason.isBlank() || cancelReason.length() > 200
                || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 300) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CANCEL_REQUEST", "결제 취소 요청 값을 확인해 주세요.");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "TOSS_SECRET_KEY_NOT_CONFIGURED", "결제 서버 설정이 완료되지 않았습니다.");
        }

        String credentials = Base64.getEncoder().encodeToString(
                (secretKey + ":").getBytes(StandardCharsets.UTF_8));
        try {
            TossPaymentResponse response = restClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(Map.of("cancelReason", cancelReason))
                    .retrieve()
                    .body(TossPaymentResponse.class);
            if (response == null || !paymentKey.equals(response.paymentKey())
                    || !"CANCELED".equals(response.status())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "INVALID_TOSS_CANCEL_RESPONSE", "결제 취소 결과를 확인할 수 없습니다.");
            }
            return response;
        } catch (RestClientResponseException exception) {
            TossErrorResponse tossError = readTossError(exception.getResponseBodyAsString());
            log.error("Toss payment cancellation failed: status={}, code={}, message={}",
                    exception.getStatusCode().value(), tossError.code(), tossError.message());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TOSS_CANCEL_FAILED",
                    "토스페이먼츠 결제 취소에 실패했습니다. [%s] %s"
                            .formatted(tossError.code(), tossError.message()));
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
