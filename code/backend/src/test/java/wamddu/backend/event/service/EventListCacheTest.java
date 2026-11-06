package wamddu.backend.event.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisHashCommands;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import wamddu.backend.event.dto.response.EventListResponse;
import wamddu.backend.global.response.ApiResponse;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventListCacheTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ConcurrentHashMap<String, byte[]> stored = new ConcurrentHashMap<>();
    private EventListCache cache;
    private RedisHashCommands commands;
    private RedisScriptingCommands scripts;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        var connection = mock(RedisConnection.class);
        commands = mock(RedisHashCommands.class);
        scripts = mock(RedisScriptingCommands.class);
        when(connection.hashCommands()).thenReturn(commands);
        when(connection.scriptingCommands()).thenReturn(scripts);
        when(redis.execute(any(RedisCallback.class))).thenAnswer(call ->
                ((RedisCallback<?>) call.getArgument(0)).doInRedis(connection));
        when(commands.hGet(any(byte[].class), any(byte[].class))).thenAnswer(call ->
                stored.get(new String(call.getArgument(1), StandardCharsets.UTF_8)));
        when(scripts.eval(any(byte[].class), any(), eq(1), any(byte[][].class))).thenAnswer(call -> {
            stored.put(new String(call.getArgument(4), StandardCharsets.UTF_8), call.getArgument(5));
            return 1L;
        });
        when(redis.delete(EventListCache.KEY)).thenAnswer(call -> { stored.clear(); return true; });
        cache = new EventListCache(redis, new ObjectMapper(), true);
    }

    @Test
    void concurrentMissesOnlyLoadOnce() throws Exception {
        var start = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var loads = new AtomicInteger();
        try (var workers = Executors.newFixedThreadPool(12)) {
            Callable<byte[]> request = () -> {
                start.await();
                return cache.getOrLoad("all:latest:0:30", () -> {
                    loads.incrementAndGet();
                    entered.countDown();
                    try { release.await(); } catch (InterruptedException e) { throw new IllegalStateException(e); }
                    return response();
                });
            };
            var futures = java.util.stream.IntStream.range(0, 12).mapToObj(i -> workers.submit(request)).toList();
            start.countDown();
            try {
                assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
            byte[] expected = futures.getFirst().get();
            for (var future : futures) assertThat(future.get()).isEqualTo(expected);
        }
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void largeCachedRepliesHaveBoundedConcurrentRedisReads() throws Exception {
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(64);
        var active = new AtomicInteger();
        var peak = new AtomicInteger();
        when(commands.hGet(any(byte[].class), any(byte[].class))).thenAnswer(call -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            entered.countDown();
            try { release.await(); } finally { active.decrementAndGet(); }
            return new byte[]{1};
        });
        try (var workers = Executors.newFixedThreadPool(64)) {
            var futures = java.util.stream.IntStream.range(0, 64)
                    .mapToObj(i -> workers.submit(() -> cache.getOrLoad("all:latest:0:30", this::response))).toList();
            try {
                entered.await(1, java.util.concurrent.TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            for (var future : futures) assertThat(future.get()).isEqualTo(new byte[]{1});
        }
        assertThat(peak.get()).isLessThanOrEqualTo(32);
    }

    @Test
    void onlyCommittedWritesEvictTheEntry() {
        cache.getOrLoad("all:latest:0:30", this::response);
        cache.getOrLoad("concert:latest:1:30", this::response);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(
                new DriverManagerDataSource("jdbc:h2:mem:event-cache-invalidation;DB_CLOSE_DELAY=-1", "sa", "")));
        tx.executeWithoutResult(status -> {
            cache.invalidateAfterCommit();
            assertThat(stored).hasSize(2);
            status.setRollbackOnly();
        });
        assertThat(stored).hasSize(2);
        tx.executeWithoutResult(status -> {
            cache.invalidateAfterCommit();
            assertThat(stored).hasSize(2);
        });
        assertThat(stored).isEmpty();
        verify(redis, times(1)).delete(EventListCache.KEY);
    }

    @Test
    void aFailedDatabaseLoadIsNotCached() {
        assertThatThrownBy(() -> cache.getOrLoad("all:latest:0:30", () -> { throw new IllegalStateException("DB failed"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(stored).isEmpty();
        assertThat(cache.getOrLoad("all:latest:0:30", this::response)).isNotEmpty();
    }

    @Test
    void disabledCacheDoesNotContactRedis() {
        cache = new EventListCache(redis, new ObjectMapper(), false);
        cache.getOrLoad("all:latest:0:30", this::response);
        cache.invalidateAfterCommit();
        verifyNoInteractions(redis);
    }

    @Test
    void failedRedisWritesStillReturnTheDatabaseResponse() {
        when(scripts.eval(any(byte[].class), any(), eq(1), any(byte[][].class)))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"));
        assertThat(cache.getOrLoad("all:latest:0:30", this::response)).isNotEmpty();
        assertThat(stored).isEmpty();
    }

    @Test
    void failedEvictionDoesNotTurnACommittedWriteIntoAnError() {
        cache.getOrLoad("all:latest:0:30", this::response);
        when(redis.delete(EventListCache.KEY))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(
                new DriverManagerDataSource("jdbc:h2:mem:event-cache-failed-eviction;DB_CLOSE_DELAY=-1", "sa", "")));
        assertThatCode(() -> tx.executeWithoutResult(status -> cache.invalidateAfterCommit()))
                .doesNotThrowAnyException();
    }

    private ApiResponse<EventListResponse> response() {
        return ApiResponse.success("조회된 이벤트가 없습니다.", new EventListResponse(List.of()));
    }
}
