package wamddu.backend.payment.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import wamddu.backend.global.exception.ApiException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TossPaymentsClientTest {
    private HttpServer server;
    private TossPaymentsClient client;
    private final AtomicReference<String> request = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> idempotencyKey = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        client = new TossPaymentsClient("http://127.0.0.1:" + server.getAddress().getPort(), "test_secret");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void respond(int status, String json) {
        server.createContext("/", exchange -> {
            request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
    }

    @Test
    void cancelSendsFullCancellationWithAuthenticationAndIdempotencyKey() {
        respond(200, "{\"paymentKey\":\"pay_1\",\"orderId\":\"order_1\",\"status\":\"CANCELED\",\"totalAmount\":1000}");

        var response = client.cancel("pay_1", "Inventory unavailable", "cancel_1");

        assertThat(response.status()).isEqualTo("CANCELED");
        assertThat(request.get()).isEqualTo("POST /v1/payments/pay_1/cancel");
        assertThat(authorization.get()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("test_secret:".getBytes(StandardCharsets.UTF_8)));
        assertThat(idempotencyKey.get()).isEqualTo("cancel_1");
        assertThat(body.get()).contains("\"cancelReason\":\"Inventory unavailable\"")
                .doesNotContain("cancelAmount");
    }

    @Test
    void cancelMapsProviderError() {
        respond(400, "{\"code\":\"FAILED_CANCEL_PAYMENT\",\"message\":\"Cancel failed\"}");
        assertThatThrownBy(() -> client.cancel("pay_1", "reason", "cancel_1"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("TOSS_CANCEL_FAILED");
    }

    @Test
    void cancelDoesNotAcceptAnUnconfirmedCancellation() {
        respond(200, "{\"paymentKey\":\"pay_1\",\"status\":\"DONE\"}");
        assertThatThrownBy(() -> client.cancel("pay_1", "reason", "cancel_1"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_TOSS_CANCEL_RESPONSE");
    }

    @Test
    void cancelRequiresReasonAndIdempotencyKey() {
        assertThatThrownBy(() -> client.cancel("pay_1", "", "cancel_1"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_CANCEL_REQUEST");
        assertThatThrownBy(() -> client.cancel("pay_1", "reason", null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_CANCEL_REQUEST");
    }
}
