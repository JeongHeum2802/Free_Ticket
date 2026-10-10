package wamddu.backend.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.user.domain.EmailVerification;
import wamddu.backend.user.repository.EmailVerificationRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EmailVerificationCollationTest {
    @Test
    void accentInsensitiveDatabaseMatchDoesNotVerifyAnotherMailbox() throws Exception {
        String token = "a".repeat(43);
        var stored = new EmailVerification();
        stored.setEmail("resume@example.com");
        stored.setTokenHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))));
        stored.setExpiresAt(Instant.now().plusSeconds(300));
        stored.setVerifiedAt(Instant.now());
        var repository = mock(EmailVerificationRepository.class);
        // MySQL utf8mb4_0900_ai_ci returns this row for the accented spelling too.
        when(repository.findForUpdate("résume@example.com")).thenReturn(Optional.of(stored));
        var service = new EmailVerificationService(repository, PasswordEncoderFactories.createDelegatingPasswordEncoder(),
                new DefaultListableBeanFactory().getBeanProvider(JavaMailSender.class));
        assertThatThrownBy(() -> service.consume("résume@example.com", token))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_EMAIL_VERIFICATION");
    }
}
