package wamddu.backend.global.exception;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import wamddu.backend.chat.TicketChatService;
import wamddu.backend.event.domain.Event;
import wamddu.backend.global.security.JwtProvider;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.dto.request.CreateOrderRequest;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.order.service.OrderService;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.user.domain.Role;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.domain.UserStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Uses a real HTTP server: errors that escape a controller are re-dispatched to /error by the
 * servlet container, which MockMvc does not reproduce.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:error_response_test;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DirtiesContext
class ErrorResponseIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired JwtProvider jwt;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orders;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean TossPaymentsClient toss;
    @MockitoBean TicketChatService chat;

    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();
    String userToken;
    Long userId;

    record Result(int status, JsonNode body, String raw) {}

    @BeforeEach
    void setUp() {
        userId = new TransactionTemplate(manager).execute(s -> {
            User user = User.builder().username("Error test").password("test-only")
                    .email("error-" + System.nanoTime() + "@example.test").customerKey("c_" + System.nanoTime())
                    .role(Role.USER).status(UserStatus.ACTIVE).build();
            em.persist(user);
            return user.getId();
        });
        userToken = jwt.generateJwtToken(userId, "USER");
    }

    Result call(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        String raw = response.body() == null ? "" : response.body();
        return new Result(response.statusCode(), raw.isBlank() ? null : json.readTree(raw), raw);
    }

    void assertError(Result result, int status, String code) {
        assertThat(result.status()).as(result.raw()).isEqualTo(status);
        assertThat(result.body()).as("JSON error body").isNotNull();
        assertThat(result.body().path("code").asString()).isEqualTo(code);
        assertThat(result.body().path("message").asString()).isNotBlank();
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        assertError(call("POST", "/api/orders", userToken, "{not-json"), 400, "INVALID_REQUEST");
    }

    @Test
    void pathVariableTypeMismatchIsBadRequest() throws Exception {
        assertError(call("GET", "/api/events/not-a-number", null, null), 400, "INVALID_REQUEST");
    }

    @Test
    void unsupportedMethodIsMethodNotAllowed() throws Exception {
        assertError(call("GET", "/api/auth/login", null, null), 405, "METHOD_NOT_ALLOWED");
    }

    @Test
    void unknownPathIsNotFound() throws Exception {
        assertError(call("GET", "/api/orders/does/not/exist", userToken, null), 404, "NOT_FOUND");
    }

    @Test
    void unexpectedExceptionIsInternalErrorWithoutLeakingDetails() throws Exception {
        when(chat.chat(any())).thenThrow(new IllegalStateException("db-password=leaked-detail"));
        Result result = call("POST", "/api/events/chat", null, "{\"messages\":[\"hello\"]}");
        assertError(result, 500, "INTERNAL_SERVER_ERROR");
        assertThat(result.raw()).doesNotContain("leaked-detail");
    }

    @Test
    void anonymousRequestToProtectedPathIsUnauthorizedJson() throws Exception {
        // The JWT filter skips /api/events/**, so this path reaches the authorization rules unauthenticated.
        assertError(call("GET", "/api/events/1/unknown", null, null), 401, "UNAUTHORIZED");
    }

    @Test
    void nonAdminIsForbiddenJson() throws Exception {
        assertError(call("GET", "/api/admin/tables", userToken, null), 403, "FORBIDDEN");
    }

    @Test
    void tossTimeoutDuringConfirmIsRecoveryPendingAndKeepsOrderConfirming() throws Exception {
        Long ticketId = new TransactionTemplate(manager).execute(s -> {
            Event event = new Event();
            event.setName("Timeout test");
            event.setStartDate(LocalDate.now().plusDays(1));
            event.setEndDate(LocalDate.now().plusDays(2));
            event.setLocation("venue");
            event.setBannerImageUrl("banner");
            event.setMainImageUrl("main");
            em.persist(event);
            Ticket ticket = new Ticket();
            ticket.setEvent(event);
            ticket.setType("ticket");
            ticket.setPrice(10000);
            ticket.setTotal_ticket(5);
            ticket.setSold_ticket(0);
            ticket.setBookingEndtime(LocalDateTime.now().plusDays(1));
            em.persist(ticket);
            return ticket.getId();
        });
        CreateOrderRequest create = new CreateOrderRequest();
        create.setTicketId(ticketId);
        create.setQuantity(1);
        String orderId = orderService.createOrder(create, userId).orderId();
        when(toss.confirm(any(), any(), any(), any())).thenThrow(new ResourceAccessException("Read timed out"));

        Result result = call("POST", "/api/payments/confirm", userToken,
                "{\"paymentKey\":\"pk_timeout\",\"orderId\":\"" + orderId + "\",\"amount\":10000}");

        assertError(result, 503, "PAYMENT_RECOVERY_PENDING");
        OrderStatus status = new TransactionTemplate(manager)
                .execute(s -> orders.findByOrderId(orderId).orElseThrow().getStatus());
        assertThat(status).isEqualTo(OrderStatus.CONFIRMING);
    }
}
