package wamddu.backend.user.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.domain.UserStatus;
import wamddu.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:password_recovery;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.mail.from=sender@example.com", "spring.mail.password=test-only"})
@AutoConfigureMockMvc
class PasswordRecoveryTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired RefreshTokenStore refreshTokens;
    @MockitoBean JavaMailSender mail;
    final List<SimpleMailMessage> sent = new ArrayList<>();
    final ObjectMapper json = new ObjectMapper();
    static final String ROOT = "/api/auth/password-resets";
    static final String NEW = "New-password123!";

    @BeforeEach
    void captureMail() {
        doAnswer(call -> { sent.add(new SimpleMailMessage(call.getArgument(0))); return null; })
                .when(mail).send(any(SimpleMailMessage.class));
    }

    User account() {
        return users.saveAndFlush(User.builder().username("회원").email(UUID.randomUUID() + "@example.com")
                .password(encoder.encode("Old-password123!")).build());
    }

    ResultActions request(String path, Map<String, String> body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    String send(String root, String email) throws Exception {
        String body = request(root, Map.of("email", email)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.verificationToken");
    }

    String code() {
        var matcher = java.util.regex.Pattern.compile("[0-9]{6}").matcher(sent.getLast().getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    ResultActions verify(String root, String email, String token, String code) throws Exception {
        return request(root + "/verify", Map.of("email", email, "verificationToken", token, "code", code));
    }

    ResultActions reset(String email, String token, String password, String confirmation) throws Exception {
        return request(ROOT, Map.of("email", email, "verificationToken", token,
                "newPassword", password, "confirmPassword", confirmation));
    }

    String proof(User user) throws Exception {
        String token = send(ROOT + "/email", user.getEmail());
        verify(ROOT + "/email", user.getEmail(), token, code()).andExpect(status().isOk());
        return token;
    }

    @Test
    void verifiedEmailCanResetOnceAndRevokesExistingRefreshTokens() throws Exception {
        var user = account();
        refreshTokens.save(UUID.randomUUID().toString(), user.getId(), "old-refresh", Instant.now().plusSeconds(600));
        String token = proof(user);
        reset(user.getEmail(), token, NEW, NEW).andExpect(status().isOk());
        assertThat(encoder.matches(NEW, users.findById(user.getId()).orElseThrow().getPassword())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ?", Integer.class, user.getId())).isZero();
        reset(user.getEmail(), token, "Another-password!", "Another-password!").andExpect(status().isBadRequest());
        request("/api/auth/login", Map.of("email", user.getEmail(), "password", NEW)).andExpect(status().isOk());
        request("/api/auth/login", Map.of("email", user.getEmail(), "password", "Old-password123!"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unverifiedExpiredAndMismatchedProofsCannotChangePassword() throws Exception {
        var user = account();
        String token = send(ROOT + "/email", user.getEmail());
        reset(user.getEmail(), token, NEW, NEW).andExpect(status().isBadRequest());
        verify(ROOT + "/email", user.getEmail(), token, code()).andExpect(status().isOk());
        reset(account().getEmail(), token, NEW, NEW).andExpect(status().isBadRequest());
        jdbc.update("update email_verifications set expires_at = ? where email = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), user.getEmail());
        reset(user.getEmail(), token, NEW, NEW).andExpect(status().isBadRequest());
        assertThat(encoder.matches("Old-password123!", users.findById(user.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test
    void ordinaryEmailProofCannotResetPasswordAndResetProofCannotSignUp() throws Exception {
        var user = account();
        String ordinary = send("/api/auth/email-verifications", user.getEmail());
        verify("/api/auth/email-verifications", user.getEmail(), ordinary, code()).andExpect(status().isOk());
        reset(user.getEmail(), ordinary, NEW, NEW).andExpect(status().isBadRequest());
        String email = UUID.randomUUID() + "@example.com";
        String token = send(ROOT + "/email", email);
        String code = code();
        verify("/api/auth/email-verifications", email, token, code).andExpect(status().isBadRequest());
        verify(ROOT + "/email", email, token, code).andExpect(status().isOk());
        request("/api/auth/signup", Map.of("email", email, "emailVerificationToken", token, "username", "회원",
                "password", NEW, "phonenumber", "01012345678")).andExpect(status().isBadRequest());
        reset(email, token, NEW, NEW).andExpect(status().isBadRequest());
    }

    @Test
    void invalidPasswordsDoNotConsumeProofOrModifyAccount() throws Exception {
        var user = account();
        String token = proof(user);
        for (String password : List.of("", "short", "has space123", "has\u001ccontrol123", "a".repeat(73), "가".repeat(25), "Old-password123!")) {
            reset(user.getEmail(), token, password, password).andExpect(status().isBadRequest());
        }
        reset(user.getEmail(), token, NEW, "different").andExpect(status().isBadRequest());
        assertThat(encoder.matches("Old-password123!", users.findById(user.getId()).orElseThrow().getPassword())).isTrue();
        reset(user.getEmail(), token, NEW, NEW).andExpect(status().isOk());
    }

    @Test
    void fiveWrongCodesStayBlockedEvenWithTheCorrectCode() throws Exception {
        var user = account();
        String token = send(ROOT + "/email", user.getEmail());
        String correct = code();
        String wrong = correct.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) verify(ROOT + "/email", user.getEmail(), token, wrong).andExpect(status().isBadRequest());
        verify(ROOT + "/email", user.getEmail(), token, correct).andExpect(status().isBadRequest());
        reset(user.getEmail(), token, NEW, NEW).andExpect(status().isBadRequest());
    }

    @Test
    void resendingInvalidatesEarlierProofAndSharesCooldownAcrossPurposes() throws Exception {
        var user = account();
        String oldToken = proof(user);
        request(ROOT + "/email", Map.of("email", user.getEmail())).andExpect(status().isTooManyRequests());
        request("/api/auth/email-verifications", Map.of("email", user.getEmail())).andExpect(status().isTooManyRequests());
        jdbc.update("update email_verifications set sent_at = ? where email = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(61)), user.getEmail());
        String newToken = proof(user);
        reset(user.getEmail(), oldToken, NEW, NEW).andExpect(status().isBadRequest());
        reset(user.getEmail(), newToken, NEW, NEW).andExpect(status().isOk());
    }

    @Test
    void inactiveAccountCannotBeRecovered() throws Exception {
        var user = account();
        String token = proof(user);
        user.setStatus(UserStatus.INACTIVE);
        users.saveAndFlush(user);
        reset(user.getEmail(), token, NEW, NEW).andExpect(status().isBadRequest());
    }
}
