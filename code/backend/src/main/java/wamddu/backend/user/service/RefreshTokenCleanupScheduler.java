package wamddu.backend.user.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "auth.refresh-token-cleanup.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {
    private final RefreshTokenStore refreshTokenStore;

    @Scheduled(fixedDelayString = "${auth.refresh-token-cleanup.delay-ms:60000}",
            initialDelayString = "${auth.refresh-token-cleanup.delay-ms:60000}")
    public void cleanup() {
        try {
            int count = refreshTokenStore.cleanupExpired(Instant.now());
            if (count > 0) log.info("Expired refresh tokens cleaned: {}", count);
        } catch (RuntimeException failure) {
            log.error("Expired refresh token cleanup failed. Retrying next tick.", failure);
        }
    }
}
