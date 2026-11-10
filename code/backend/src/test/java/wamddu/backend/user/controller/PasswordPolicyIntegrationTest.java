package wamddu.backend.user.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import wamddu.backend.global.security.JwtProvider;
import wamddu.backend.user.domain.Role;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.domain.UserStatus;
import wamddu.backend.user.repository.UserRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:password_policy_test;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@DirtiesContext
class PasswordPolicyIntegrationTest {
    static final String ORIGINAL = "Original-pass1";

    @Value("${local.server.port}") int port;
    @Autowired JwtProvider jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;

    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();
    Long userId;
    String token;

    record Result(int status, JsonNode body) {}

    @BeforeEach
    void setUp() {
        userId = new TransactionTemplate(manager).execute(s -> {
            User user = User.builder().username("Policy").password(encoder.encode(ORIGINAL))
                    .email("policy-" + System.nanoTime() + "@example.test").customerKey("c_" + System.nanoTime())
                    .role(Role.USER).status(UserStatus.ACTIVE).build();
            em.persist(user);
            return user.getId();
        });
        token = jwt.generateJwtToken(userId, "USER");
    }

    Result call(String method, String path, Map<String, ?> body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), response.body().isBlank() ? null : json.readTree(response.body()));
    }

    Result changePassword(String current, String next) throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        if (current != null) body.put("currentPassword", current);
        if (next != null) body.put("newPassword", next);
        return call("PATCH", "/api/users/me/password", body);
    }

    boolean passwordIs(String raw) {
        return encoder.matches(raw, users.findById(userId).orElseThrow().getPassword());
    }

    static List<String> invalidPasswords() {
        // 73 ASCII bytes and 25 Hangul syllables (75 UTF-8 bytes) both exceed BCrypt's 72-byte input.
        return List.of("a", "short1!", "has space123", "tab\tinside123", "x".repeat(73), "가".repeat(25));
    }

    @ParameterizedTest
    @MethodSource("invalidPasswords")
    void changeRejectsPasswordsOutsideThePolicy(String invalid) throws Exception {
        Result result = changePassword(ORIGINAL, invalid);
        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body().path("code").asString()).isEqualTo("INVALID_PASSWORD");
        assertThat(passwordIs(ORIGINAL)).isTrue();
    }

    @Test
    void changeRejectsMissingFieldsAsBadRequest() throws Exception {
        assertThat(changePassword(ORIGINAL, null).status()).isEqualTo(400);
        Result missingCurrent = changePassword(null, "Another-pass1");
        assertThat(missingCurrent.status()).isEqualTo(400);
        assertThat(missingCurrent.body().path("code").asString()).isEqualTo("INVALID_REQUEST");
        assertThat(passwordIs(ORIGINAL)).isTrue();
    }

    @Test
    void validChangeSucceeds() throws Exception {
        assertThat(changePassword(ORIGINAL, "Another-pass1").status()).isEqualTo(200);
        assertThat(passwordIs("Another-pass1")).isTrue();
    }

    @Test
    void deleteAccountWithoutPasswordIsBadRequest() throws Exception {
        Result result = call("DELETE", "/api/users/me", Map.of());
        assertThat(result.status()).isEqualTo(400);
        assertThat(users.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"has space123", "x"})
    void signupAppliesTheSamePolicy(String invalid) throws Exception {
        signupRejects(invalid);
    }

    @Test
    void signupRejectsPasswordsOverBcryptLimit() throws Exception {
        signupRejects("x".repeat(73));
    }

    void signupRejects(String password) throws Exception {
        Result result = call("POST", "/api/auth/signup", Map.of(
                "username", "Signup", "password", password, "email", "signup@example.test",
                "phonenumber", "01012345678", "emailVerificationToken", "a".repeat(43)));
        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body().path("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(result.body().path("errors").has("password")).isTrue();
        assertThat(result.body().path("errors").size()).isEqualTo(1);
    }
}
