package wamddu.backend.user.service;

import java.util.UUID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.jdbc.core.JdbcTemplate;
import com.jayway.jsonpath.JsonPath;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.repository.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:email_verification;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.mail.from=sender@example.com", "spring.mail.password=test-only"})
@AutoConfigureMockMvc
class EmailVerificationFlowTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired wamddu.backend.global.security.JwtProvider jwt;
    @Autowired wamddu.backend.admin.AdminTableService admin;
    @Autowired EmailVerificationService verificationService;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @MockitoBean JavaMailSender mail;
    final List<SimpleMailMessage> sent = new ArrayList<>();

    @BeforeEach
    void captureMail() {
        doAnswer(call -> { sent.add(new SimpleMailMessage(call.getArgument(0))); return null; })
                .when(mail).send(any(SimpleMailMessage.class));
    }

    private String email() { return UUID.randomUUID() + "@example.com"; }

    private String send(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(sent.getLast().getTo()).containsExactly(email.toLowerCase(java.util.Locale.ROOT));
        return JsonPath.read(body, "$.verificationToken");
    }

    private String code() {
        var matcher = java.util.regex.Pattern.compile("[0-9]{6}").matcher(sent.getLast().getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    private org.springframework.test.web.servlet.ResultActions verify(String email, String token, String code) throws Exception {
        return mvc.perform(post("/api/auth/email-verifications/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"verificationToken\":\"%s\",\"code\":\"%s\"}".formatted(email, token, code)));
    }

    private org.springframework.test.web.servlet.ResultActions signup(String email, String token) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"회원", "password":"password123!", "email":"%s", "phonenumber":"%s", "emailVerificationToken":"%s"}
                """.formatted(email, "010" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode() % 100000000)), token)));
    }

    @Test
    void signupWithoutEmailProofIsRejected() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"회원", "password":"password123!", "email":"%s", "phonenumber":"01055555555"}
                    """.formatted(UUID.randomUUID() + "@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingEmailWithoutProofIsRejected() throws Exception {
        var account = users.saveAndFlush(User.builder().username("회원").password("unused")
                .email(UUID.randomUUID() + "@example.com").build());
        mvc.perform(post("/api/users/me").header("Authorization", "Bearer " + jwt.generateJwtToken(account.getId(), "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + UUID.randomUUID() + "@example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifiedProofCreatesVerifiedUserAndCannotBeUsedTwice() throws Exception {
        String email = email();
        String token = send(email);
        signup(email, token).andExpect(status().isBadRequest());
        verify(email, token, code()).andExpect(status().isOk());
        signup(email, token).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select email_verified_at from users where email = ?", java.sql.Timestamp.class, email)).isNotNull();
        assertThat(jdbc.queryForObject("select consumed_at from email_verifications where email = ?", java.sql.Timestamp.class, email)).isNotNull();
        verify(email, token, code()).andExpect(status().isBadRequest());
    }

    @Test
    void proofCannotBeUsedForAnotherEmailOrWithForgedToken() throws Exception {
        String email = email();
        String token = send(email);
        verify(email, "x".repeat(43), code()).andExpect(status().isBadRequest());
        verify(email, token, code()).andExpect(status().isOk());
        signup(email(), token).andExpect(status().isBadRequest());
        signup(email, token).andExpect(status().isCreated());
    }

    @Test
    void failedAttemptsPersistAndFifthFailureLocksCode() throws Exception {
        String email = email();
        String token = send(email);
        String correct = code();
        String wrong = correct.equals("000000") ? "111111" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) verify(email, token, wrong).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select failed_attempts from email_verifications where email = ?", Integer.class, email)).isEqualTo(5);
        verify(email, token, correct).andExpect(status().isBadRequest());
    }

    @Test
    void expiredProofCannotVerifyOrSignup() throws Exception {
        String email = email();
        String token = send(email);
        verify(email, token, code()).andExpect(status().isOk());
        jdbc.update("update email_verifications set expires_at = ? where email = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), email);
        verify(email, token, code()).andExpect(status().isBadRequest());
        signup(email, token).andExpect(status().isBadRequest());
    }

    @Test
    void resendIsThrottledAndReplacesOldProof() throws Exception {
        String email = email();
        String oldToken = send(email);
        String oldCode = code();
        verify(email, oldToken, oldCode).andExpect(status().isOk());
        mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}")).andExpect(status().isTooManyRequests());
        jdbc.update("update email_verifications set sent_at = ? where email = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(61)), email);
        String newToken = send(email);
        signup(email, oldToken).andExpect(status().isBadRequest());
        verify(email, oldToken, oldCode).andExpect(status().isBadRequest());
        verify(email, newToken, code()).andExpect(status().isOk());
        signup(email, newToken).andExpect(status().isCreated());
    }

    @Test
    void mailFailureDoesNotLeaveAUsableChallenge() throws Exception {
        String email = email();
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}")).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("select count(*) from email_verifications where email = ?", Integer.class, email)).isZero();
    }

    @Test
    void validProofChangesEmailAndUnchangedEmailNeedsNoProof() throws Exception {
        var account = users.saveAndFlush(User.builder().username("회원").password("unused").email(email()).build());
        String email = email();
        String token = send(email);
        verify(email, token, code()).andExpect(status().isOk());
        mvc.perform(post("/api/users/me").header("Authorization", "Bearer " + jwt.generateJwtToken(account.getId(), "USER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"emailVerificationToken\":\"%s\"}".formatted(email, token)))
                .andExpect(status().isOk());
        var changed = users.findById(account.getId()).orElseThrow();
        assertThat(changed.getEmail()).isEqualTo(email);
        assertThat(changed.getEmailVerifiedAt()).isNotNull();
        mvc.perform(post("/api/users/me").header("Authorization", "Bearer " + jwt.generateJwtToken(account.getId(), "USER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}")).andExpect(status().isOk());
    }

    @Test
    void invalidInputDoesNotSendMail() throws Exception {
        mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\"}")).andExpect(status().isBadRequest());
        assertThat(sent).isEmpty();
    }

    @Test
    void adminCannotForgeVerificationAndChangingEmailClearsOldVerification() {
        var account = users.saveAndFlush(User.builder().username("회원").password("unused").email(email())
                .emailVerifiedAt(Instant.now()).build());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> admin.update("users", List.of(
                new wamddu.backend.admin.AdminTableService.RowChange(account.getId().toString(),
                        java.util.Map.of("email_verified_at", "unused"), java.util.Map.of("email_verified_at", "2026-10-10 00:00:00"))), 0, 100))
                .isInstanceOf(wamddu.backend.global.exception.ApiException.class)
                .extracting("code").isEqualTo("ADMIN_FIELD_READ_ONLY");
        admin.update("users", List.of(new wamddu.backend.admin.AdminTableService.RowChange(account.getId().toString(),
                java.util.Map.of("email", account.getEmail()), java.util.Map.of("email", email()))), 0, 100);
        assertThat(users.findById(account.getId()).orElseThrow().getEmailVerifiedAt()).isNull();
    }

    @Test
    void concurrentConsumptionOnlySucceedsOnce() throws Exception {
        String email = email();
        String token = send(email);
        verify(email, token, code()).andExpect(status().isOk());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> consume = () -> {
                start.await();
                try {
                    transactions.executeWithoutResult(status -> verificationService.consume(email, token));
                    return true;
                } catch (wamddu.backend.global.exception.ApiException rejected) { return false; }
            };
            var first = pool.submit(consume);
            var second = pool.submit(consume);
            start.countDown();
            assertThat(List.of(first.get(5, java.util.concurrent.TimeUnit.SECONDS), second.get(5, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void rolledBackAccountWriteDoesNotConsumeProof() throws Exception {
        String email = email();
        String token = send(email);
        verify(email, token, code()).andExpect(status().isOk());
        transactions.executeWithoutResult(status -> {
            verificationService.consume(email, token);
            status.setRollbackOnly();
        });
        signup(email, token).andExpect(status().isCreated());
    }

    @Test
    void hourlyEmailLimitStillAppliesAfterResendCooldown() throws Exception {
        String email = email();
        send(email);
        jdbc.update("update email_verifications set send_count = 5, sent_at = ? where email = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(61)), email);
        mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}")).andExpect(status().isTooManyRequests());
        assertThat(sent).hasSize(1);
    }
}
