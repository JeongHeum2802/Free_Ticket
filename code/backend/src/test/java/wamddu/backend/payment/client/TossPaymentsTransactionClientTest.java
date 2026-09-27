package wamddu.backend.payment.client;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TossPaymentsTransactionClientTest {
    HttpServer server;
    TossPaymentsClient client;
    AtomicReference<String> query = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        client = new TossPaymentsClient("http://127.0.0.1:" + server.getAddress().getPort(), "test_secret");
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    private void respond(int status, String body) {
        server.createContext("/v1/transactions", exchange -> {
            query.set(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
    }

    @Test
    void sendsDateRangeCursorLimitAndBasicAuth() {
        respond(200, "[{\"mId\":\"mid\",\"transactionKey\":\"tx-1\",\"paymentKey\":\"pay-1\","
                + "\"orderId\":\"ORD-1\",\"status\":\"CANCELED\",\"transactionAt\":\"2026-01-01T01:00:00+09:00\"}]");
        var start = LocalDateTime.parse("2026-01-01T00:00:00");
        var end = start.plusDays(1);
        var result = client.getTransactions(start, end, "tx-before", 500);
        assertThat(query.get()).contains("startDate=2026-01-01T00:00:00", "endDate=2026-01-02T00:00:00",
                "startingAfter=tx-before", "limit=500");
        assertThat(authorization.get()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("test_secret:".getBytes(StandardCharsets.UTF_8)));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().transactionKey()).isEqualTo("tx-1");
        assertThat(result.getFirst().status()).isEqualTo("CANCELED");
    }

    @Test
    void firstPageOmitsCursorAndProviderFailureFailsWindow() {
        respond(429, "{\"code\":\"TOO_MANY_REQUESTS\"}");
        var start = LocalDateTime.parse("2026-01-01T00:00:00");
        assertThatThrownBy(() -> client.getTransactions(start, start.plusDays(1), null, 500))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("429");
        assertThat(query.get()).doesNotContain("startingAfter");
    }
}
