package wamddu.backend.user.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import wamddu.backend.user.domain.RefreshToken;
import wamddu.backend.user.repository.RefreshTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:refresh_cleanup;MODE=MySQL;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RefreshTokenCleanupTest {
    @Autowired RefreshTokenStore store;
    @MockitoSpyBean RefreshTokenRepository tokens;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearTokens() {
        tokens.deleteAllInBatch();
    }

    @Test
    void savingNewTokenDoesNotDeleteUnrelatedExpiredRows() {
        tokens.saveAndFlush(new RefreshToken("expired", 1L, "a".repeat(64), Instant.now().minusSeconds(60)));
        Instant expiresAt = Instant.parse("2099-01-01T00:00:00Z");

        store.save("new-family", 2L, "abc", expiresAt);

        assertThat(tokens.existsById("expired")).isTrue();
        var saved = tokens.findById("new-family").orElseThrow();
        assertThat(saved.getTokenHash()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void cleanupIncludesTheCutoffAndPreservesFutureTokens() {
        Instant cutoff = Instant.parse("2026-11-01T00:00:00Z");
        tokens.saveAllAndFlush(List.of(token("before", cutoff.minusSeconds(1)), token("at", cutoff),
                token("future", cutoff.plusNanos(1000))));

        assertThat(store.cleanupExpired(cutoff)).isEqualTo(2);

        assertThat(tokens.findAll()).extracting(RefreshToken::getId).containsExactly("future");
        assertThat(store.cleanupExpired(cutoff)).isZero();
    }

    @Test
    void cleanupRemovesAtMostOneThousandOldestTokensAndLaterCallsDrainTheRemainder() {
        Instant cutoff = Instant.parse("2026-11-01T00:00:00Z");
        var expired = new ArrayList<RefreshToken>();
        for (int i = 0; i < 2000; i++) expired.add(token("expired-%04d".formatted(i), cutoff));
        expired.add(token("oldest", cutoff.minusSeconds(1)));
        tokens.saveAllAndFlush(expired);
        store.save("new-login", 2L, "new-token", cutoff.plusSeconds(3600));

        assertThat(store.cleanupExpired(cutoff)).isEqualTo(1000);
        assertThat(tokens.existsById("oldest")).isFalse();
        assertThat(tokens.existsById("expired-0998")).isFalse();
        assertThat(tokens.existsById("expired-0999")).isTrue();
        assertThat(tokens.count()).isEqualTo(1002);

        assertThat(store.cleanupExpired(cutoff)).isEqualTo(1000);
        assertThat(tokens.existsById("expired-1999")).isTrue();
        assertThat(tokens.count()).isEqualTo(2);
        assertThat(store.cleanupExpired(cutoff)).isEqualTo(1);
        assertThat(tokens.findAll()).extracting(RefreshToken::getId).containsExactly("new-login");
    }

    @Test
    void cleanupCommitsIndependentlyOfAnOuterTransactionRollback() {
        Instant cutoff = Instant.parse("2026-11-01T00:00:00Z");
        tokens.saveAndFlush(token("expired", cutoff));

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(store.cleanupExpired(cutoff)).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(tokens.existsById("expired")).isFalse();
    }

    @Test
    void emptyCleanupDoesNotIssueADelete() {
        assertThat(store.cleanupExpired(Instant.now())).isZero();

        verify(tokens, never()).deleteExpiredByIds(any(), any());
    }

    @Test
    void cleanupRechecksExpiryAfterSelectingTheBatch() throws Throwable {
        Instant cutoff = Instant.parse("2026-11-01T00:00:00Z");
        tokens.saveAndFlush(token("extended", cutoff));
        doAnswer(invocation -> {
            Object selected = mockingDetails(invocation.getMock()).getMockCreationSettings()
                    .getDefaultAnswer().answer(invocation);
            jdbc.update("update refresh_tokens set expires_at = ? where id = ?",
                    java.sql.Timestamp.from(cutoff.plusSeconds(60)), "extended");
            return selected;
        }).when(tokens).findExpiredIds(eq(cutoff), any());

        assertThat(store.cleanupExpired(cutoff)).isZero();

        assertThat(tokens.findById("extended").orElseThrow().getExpiresAt()).isEqualTo(cutoff.plusSeconds(60));
    }

    @Test
    void cleanupSchedulerIsDisabledInTheDefaultTestConfiguration() {
        assertThat(context.containsBean("refreshTokenCleanupScheduler")).isFalse();
    }

    private RefreshToken token(String id, Instant expiresAt) {
        return new RefreshToken(id, 1L, "a".repeat(64), expiresAt);
    }
}
