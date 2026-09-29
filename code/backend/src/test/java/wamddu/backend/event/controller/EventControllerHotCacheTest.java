package wamddu.backend.event.controller;

import org.junit.jupiter.api.Test;
import wamddu.backend.event.dto.response.WhatsHotEventResponse;
import wamddu.backend.event.dto.response.WhatsHotResponse;
import wamddu.backend.event.service.EventService;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EventControllerHotCacheTest {
    @Test
    void concurrentRequestsForSameKeyCalculateHotOnce() throws Exception {
        var service = mock(EventService.class);
        var expected = new WhatsHotResponse(null, List.of());
        when(service.whatshot(null, 7)).thenAnswer(invocation -> {
            Thread.sleep(100);
            return expected;
        });
        var controller = new EventController(service);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(20)) {
            List<Callable<WhatsHotResponse>> requests = java.util.stream.IntStream.range(0, 20)
                    .<Callable<WhatsHotResponse>>mapToObj(i -> () -> {
                        start.await();
                        return controller.whatshot(null, 7).getData();
                    }).toList();
            var futures = requests.stream().map(workers::submit).toList();
            start.countDown();
            for (var future : futures) assertThat(future.get()).isSameAs(expected);
        }
        verify(service, times(1)).whatshot(null, 7);
        controller.whatshot(null, 5);
        verify(service, times(1)).whatshot(null, 5);
    }

    @Test
    void weeklyRankingUsesTheSameCachedHotOrderAndRanks() {
        var service = mock(EventService.class);
        var early = new WhatsHotEventResponse(1L, 2L, "early", LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 1), "Seoul", "banner-2", "poster-2", "musical");
        var late = new WhatsHotEventResponse(1L, 1L, "late", LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 12, 1), "Seoul", "banner-1", "poster-1", "musical");
        when(service.whatshot("musical", 5)).thenReturn(new WhatsHotResponse("musical", List.of(early, late)));
        var controller = new EventController(service);

        controller.whatshot("musical", 5);
        var ranking = controller.getWeeklyRanking("musical", 5).getData();

        assertThat(ranking.category()).isEqualTo("musical");
        assertThat(ranking.events()).extracting("id").containsExactly(2L, 1L);
        assertThat(ranking.events()).extracting("rank").containsExactly(1L, 1L);
        assertThat(ranking.events()).extracting("mainImageUrl").containsExactly("poster-2", "poster-1");
        verify(service, times(1)).whatshot("musical", 5);
    }
}
