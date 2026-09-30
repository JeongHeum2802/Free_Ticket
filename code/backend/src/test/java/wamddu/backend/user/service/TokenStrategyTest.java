package wamddu.backend.user.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.dto.request.ChangePasswordRequest;
import wamddu.backend.user.dto.request.DeleteAccountRequest;
import wamddu.backend.user.dto.request.LoginRequest;
import wamddu.backend.user.dto.response.LoginResult;
import wamddu.backend.user.dto.response.TokenReissueResult;
import wamddu.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:token_strategy;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "jwt.access-expiration-ms=600000",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=1000"
})
@AutoConfigureMockMvc
class TokenStrategyTest {
    private static final String SECRET = "test-secret-key-for-jwt-must-be-at-least-32-bytes";
    private static final String PASSWORD = "password123!";
    @Autowired UserService service;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    User user;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(User.builder().username("Token user")
                .email(UUID.randomUUID() + "@example.test").password(encoder.encode(PASSWORD)).build());
    }

    private LoginResult login() {
        return service.login(new LoginRequest(user.getEmail(), PASSWORD));
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
    }

    private void rejectedRefresh(String token) {
        assertThatThrownBy(() -> service.reissueToken(token)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private UserDetails principal() {
        return org.springframework.security.core.userdetails.User.withUsername(user.getId().toString())
                .password("").authorities("ROLE_USER").build();
    }

    @Test
    void refreshCannotAuthenticateAsAnAccessToken() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + login().refreshToken()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void accessCannotObtainARefreshToken() {
        rejectedRefresh(login().response().getAccessToken());
    }

    @Test
    void accessLifetimeAndAdvertisedExpiryAgree() {
        var result = login();
        var access = claims(result.response().getAccessToken());
        assertThat(result.response().getExpiresIn()).isEqualTo(600);
        assertThat(access.getExpiration().getTime() - access.getIssuedAt().getTime()).isEqualTo(600000);
        assertThat(access.get("token_use", String.class)).isEqualTo("access");
    }

    @Test
    void rotatingRefreshRejectsReplayAndPersistsFamilyRevocation() {
        var original = login().refreshToken();
        var rotated = service.reissueToken(original).refreshToken();
        assertThat(rotated).isNotEqualTo(original);
        rejectedRefresh(original);
        // The 401 exception must not roll back revocation of the replacement.
        rejectedRefresh(rotated);
    }

    @Test
    void rotationKeepsOriginalDeadlineAndStoresOnlyTheHash() {
        var original = login().refreshToken();
        var originalClaims = claims(original);
        var family = originalClaims.get("family_id", String.class);
        var deadline = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var earlier = Jwts.builder().subject(user.getId().toString()).claim("token_use", "refresh")
                .claim("family_id", family).id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(60))).expiration(Date.from(deadline))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        try {
            var hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(earlier.getBytes(StandardCharsets.UTF_8)));
            jdbc.update("update refresh_tokens set token_hash = ?, expires_at = ? where id = ?",
                    hash, java.sql.Timestamp.from(deadline), family);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        var result = service.reissueToken(earlier);
        assertThat(claims(result.refreshToken()).getExpiration().toInstant()).isEqualTo(deadline);
        assertThat(claims(result.refreshToken()).getId()).isNotEqualTo(claims(earlier).getId());
        var stored = jdbc.queryForObject("select token_hash from refresh_tokens where id = ?", String.class, family);
        assertThat(stored).hasSize(64).isNotEqualTo(result.refreshToken());
    }

    @Test
    void logoutRevokesOnlyThatLoginAndLeavesAccessValidUntilExpiry() throws Exception {
        var first = login();
        var otherLogin = login();
        var response = mvc.perform(post("/api/auth/logout").cookie(new Cookie("refreshToken", first.refreshToken())))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(value -> value.contains("Path=/api/auth;")
                && value.contains("Max-Age=0"));
        assertThat(response.getHeaders("Set-Cookie")).anyMatch(value -> value.contains("Path=/api/auth/refresh;")
                && value.contains("Max-Age=0"));
        rejectedRefresh(first.refreshToken());
        assertThat(service.reissueToken(otherLogin.refreshToken()).response().getAccessToken()).isNotBlank();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + first.response().getAccessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void passwordChangeRevokesAllRefreshTokensForTheUser() {
        var first = login();
        var second = login();
        service.updateMyPassword(new ChangePasswordRequest(PASSWORD, "newPassword123!"), principal());
        rejectedRefresh(first.refreshToken());
        rejectedRefresh(second.refreshToken());
        assertThat(service.login(new LoginRequest(user.getEmail(), "newPassword123!")).refreshToken()).isNotBlank();
    }

    @Test
    void accountDeletionRevokesRefreshTokens() {
        var original = login().refreshToken();
        service.deleteMyAccount(new DeleteAccountRequest(PASSWORD), principal());
        rejectedRefresh(original);
    }

    @Test
    void concurrentRefreshCanConsumeTheTokenOnlyOnce() throws Exception {
        var original = login().refreshToken();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Object> refresh = () -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                try { return service.reissueToken(original); }
                catch (ApiException error) { return error; }
            };
            var first = pool.submit(refresh);
            var second = pool.submit(refresh);
            start.countDown();
            var results = java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().filter(TokenReissueResult.class::isInstance).count()).isEqualTo(1);
            assertThat(results.stream().filter(ApiException.class::isInstance).count()).isEqualTo(1);
            var replacement = results.stream().filter(TokenReissueResult.class::isInstance)
                    .map(TokenReissueResult.class::cast).findFirst().orElseThrow();
            rejectedRefresh(replacement.refreshToken());
        } finally {
            pool.shutdownNow();
        }
    }
}
