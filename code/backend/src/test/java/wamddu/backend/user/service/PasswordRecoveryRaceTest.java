package wamddu.backend.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import wamddu.backend.eventDirector.repository.EventDirectorRepository;
import wamddu.backend.user.domain.EmailVerification;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.dto.request.UpdateMyInfoRequest;
import wamddu.backend.user.repository.EmailVerificationRepository;
import wamddu.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:password_recovery_race;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=500"})
class PasswordRecoveryRaceTest {
    @Autowired UserService profiles;
    @Autowired PasswordRecoveryService recovery;
    @Autowired UserRepository users;
    @Autowired EmailVerificationRepository verifications;
    @Autowired PasswordEncoder encoder;
    @MockitoBean EventDirectorRepository directors;

    @Test
    void pendingProfileWriteCannotOverwriteRecoveredPassword() throws Exception {
        var user = users.saveAndFlush(User.builder().username("Before").email(UUID.randomUUID() + "@example.com")
                .password(encoder.encode("Old-password123!")).build());
        String token = "a".repeat(43);
        var proof = new EmailVerification();
        proof.setEmail(user.getEmail());
        proof.setPurpose(EmailVerification.Purpose.PASSWORD_RESET);
        proof.setTokenHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))));
        proof.setCodeHash("unused");
        proof.setSentAt(Instant.now());
        proof.setWindowStartedAt(Instant.now());
        proof.setVerifiedAt(Instant.now());
        proof.setExpiresAt(Instant.now().plusSeconds(300));
        verifications.saveAndFlush(proof);
        var profileLoaded = new CountDownLatch(1);
        var releaseProfile = new CountDownLatch(1);
        // Pause after the profile row was read/changed, before its transaction commits.
        doAnswer(call -> {
            profileLoaded.countDown();
            if (!releaseProfile.await(10, TimeUnit.SECONDS)) throw new AssertionError("profile release timeout");
            return false;
        }).when(directors).existsByUserId(user.getId());
        var principal = org.springframework.security.core.userdetails.User.withUsername(user.getId().toString())
                .password("").authorities("ROLE_USER").build();
        var pool = Executors.newSingleThreadExecutor();
        var pendingProfile = pool.submit(() -> profiles.updateMyInfo(new UpdateMyInfoRequest("After", null, null, null), principal));
        try {
            assertThat(profileLoaded.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> recovery.reset(user.getEmail(), token, "New-password123!", "New-password123!"))
                    .isInstanceOf(PessimisticLockingFailureException.class);
        } finally {
            releaseProfile.countDown();
            pool.shutdown();
        }
        pendingProfile.get(10, TimeUnit.SECONDS);
        recovery.reset(user.getEmail(), token, "New-password123!", "New-password123!");
        var updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.getUsername()).isEqualTo("After");
        assertThat(encoder.matches("New-password123!", updated.getPassword())).isTrue();
    }
}
