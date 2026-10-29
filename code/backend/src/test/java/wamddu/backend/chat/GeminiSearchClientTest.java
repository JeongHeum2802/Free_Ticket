package wamddu.backend.chat;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import wamddu.backend.global.exception.ApiException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class GeminiSearchClientTest {
    private HttpServer server;
    private GeminiSearchClient client;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> key = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();

    @BeforeEach void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        client = new GeminiSearchClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "gemini-3.1-flash-lite");
    }

    @AfterEach void tearDown() { server.stop(0); }

    private void respond(int status, String response) {
        server.createContext("/", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            path.set(exchange.getRequestURI().toString());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
    }

    private String candidate(String text, String finishReason) {
        return json.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", finishReason,
                "content", Map.of("parts", List.of(Map.of("text", text)))))));
    }

    @Test void extractsConditionsUsingStructuredOutputAndKeepsKeyOutOfBodyAndUrl() {
        respond(200, candidate("""
                {"search":true,"category":"musical","title":null,"region":"서울",
                 "dateFrom":"2026-10-31","dateTo":"2026-11-01","maxPrice":50000,"availableOnly":true}
                """, "STOP"));
        var result = client.extract(List.of("서울에서 이번 주말 5만 원 이하 뮤지컬"));
        assertThat(result.search()).isTrue();
        assertThat(result.region()).isEqualTo("서울");
        assertThat(result.maxPrice()).isEqualTo(50000);
        assertThat(result.availableOnly()).isTrue();
        assertThat(result.dateFrom()).hasToString("2026-10-31");
        assertThat(key.get()).isEqualTo("test-key");
        assertThat(path.get()).isEqualTo("/v1beta/models/gemini-3.1-flash-lite:generateContent");
        var request = json.readTree(body.get());
        assertThat(request.at("/generationConfig/responseMimeType").asString()).isEqualTo("application/json");
        assertThat(request.at("/generationConfig/responseJsonSchema/properties/maxPrice/type").isArray()).isTrue();
        assertThat(request.at("/systemInstruction/parts/0/text").asString()).contains("Asia/Seoul");
        assertThat(body.get()).doesNotContain("test-key");
    }

    @Test void forwardsBoundedFollowUpContextAsData() {
        respond(200, candidate("""
                {"search":false,"category":null,"title":null,"region":null,
                 "dateFrom":null,"dateTo":null,"maxPrice":null,"availableOnly":null}
                """, "STOP"));
        assertThat(client.extract(List.of("서울 뮤지컬", "그럼 부산은?")).search()).isFalse();
        assertThat(json.readTree(body.get()).at("/contents/0/parts/0/text").asString())
                .contains("서울 뮤지컬", "그럼 부산은?");
    }

    @Test void quotaErrorsAreSafeAndAreNotRetried() {
        respond(429, "{\"error\":{\"message\":\"secret provider detail\"}}");
        assertThatThrownBy(() -> client.extract(List.of("서울 뮤지컬")))
                .isInstanceOf(ApiException.class).hasMessageNotContaining("secret")
                .extracting("code").isEqualTo("CHAT_RATE_LIMITED");
    }

    @Test void authenticationErrorsDoNotExposeProviderResponse() {
        respond(403, "{\"error\":{\"message\":\"test-key secret\"}}");
        assertThatThrownBy(() -> client.extract(List.of("공연")))
                .isInstanceOf(ApiException.class).hasMessageNotContaining("test-key")
                .extracting("code").isEqualTo("CHAT_UNAVAILABLE");
    }

    @Test void blockedResponseIsNotAnEmptySearch() {
        respond(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
        assertThatThrownBy(() -> client.extract(List.of("공연")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CHAT_INVALID_RESPONSE");
    }

    @Test void truncatedResponseIsRejected() {
        respond(200, candidate("{\"search\":true}", "MAX_TOKENS"));
        assertThatThrownBy(() -> client.extract(List.of("공연")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CHAT_INVALID_RESPONSE");
    }

    @Test void fractionalBudgetIsNotSilentlyTruncated() {
        respond(200, candidate("""
                {"search":true,"category":null,"title":null,"region":null,
                 "dateFrom":null,"dateTo":null,"maxPrice":2.5,"availableOnly":null}
                """, "STOP"));
        assertThatThrownBy(() -> client.extract(List.of("공연")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CHAT_INVALID_RESPONSE");
    }

    @Test void invalidJsonIsRejected() {
        respond(200, candidate("not json", "STOP"));
        assertThatThrownBy(() -> client.extract(List.of("공연")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CHAT_INVALID_RESPONSE");
    }

    @Test void missingFiltersAreNotSilentlyTurnedIntoAnUnfilteredSearch() {
        respond(200, candidate("{\"search\":true}", "STOP"));
        assertThatThrownBy(() -> client.extract(List.of("서울 뮤지컬")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CHAT_INVALID_RESPONSE");
    }

    @Test void unsupportedFiltersAreNotSilentlyDropped() {
        respond(200, candidate("""
                {"search":true,"category":null,"title":null,"region":null,
                 "dateFrom":null,"dateTo":null,"maxPrice":null,"availableOnly":null,"minPrice":50000}
                """, "STOP"));
        assertThatThrownBy(() -> client.extract(List.of("5만원 이상 공연")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CHAT_INVALID_RESPONSE");
    }

    @Test void missingKeyFailsWithoutMakingRequest() {
        var missing = new GeminiSearchClient("http://127.0.0.1:" + server.getAddress().getPort(), "", "gemini-3.1-flash-lite");
        assertThatThrownBy(() -> missing.extract(List.of("공연")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CHAT_NOT_CONFIGURED");
        assertThat(body.get()).isNull();
    }
}
