package wamddu.backend.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import wamddu.backend.user.domain.RefreshToken;
import wamddu.backend.user.repository.RefreshTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:refresh_cleanup_scheduler;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "auth.refresh-token-cleanup.enabled=true",
        "auth.refresh-token-cleanup.delay-ms=50"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RefreshTokenCleanupSchedulerTest {
    @MockitoSpyBean RefreshTokenStore store;
    @Autowired RefreshTokenRepository tokens;

    @Test
    void automaticallyScheduledCleanupRetriesAfterAFailedTick() {
        var failNext = new AtomicBoolean(true);
        var failures = new AtomicInteger();
        doAnswer(invocation -> {
            if (failNext.getAndSet(false)) {
                failures.incrementAndGet();
                throw new IllegalStateException("test cleanup failure");
            }
            return invocation.callRealMethod();
        }).when(store).cleanupExpired(any(Instant.class));
        tokens.saveAllAndFlush(List.of(
                new RefreshToken("expired", 1L, "a".repeat(64), Instant.now().minusSeconds(60)),
                new RefreshToken("future", 1L, "b".repeat(64), Instant.now().plusSeconds(3600))));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(failures.get()).isEqualTo(1);
            assertThat(tokens.existsById("expired")).isFalse();
        });
        assertThat(tokens.existsById("future")).isTrue();
    }
}
