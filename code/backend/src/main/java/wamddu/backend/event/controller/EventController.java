package wamddu.backend.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import wamddu.backend.event.dto.response.EventDetailResponse;
import wamddu.backend.event.dto.response.EventListResponse;
import wamddu.backend.event.dto.response.WeeklyRankingEventResponse;
import wamddu.backend.event.dto.response.WeeklyRankingResponse;
import wamddu.backend.event.dto.response.WhatsHotResponse;
import wamddu.backend.event.service.EventService;
import wamddu.backend.global.response.ApiResponse;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private static final long HOT_CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(5);
    // ponytail: 서버별 캐시다. 여러 서버에서 같은 갱신 시점이 필요해지면 공유 캐시를 검토한다.
    private final ConcurrentHashMap<HotKey, HotEntry> hotCache = new ConcurrentHashMap<>();

    private record HotKey(String category, Integer limit) {}
    private record HotEntry(WhatsHotResponse response, long loadedAtNanos) {}

    @GetMapping
    public ApiResponse<EventListResponse> getEvents(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "region", required = false) String region
    ) {
        EventListResponse response = eventService.getEvents(category, title, region);
        String message = response.events().isEmpty() ? "조회된 이벤트가 없습니다." : "이벤트 목록 조회에 성공했습니다.";
        return ApiResponse.success(message, response);
    }

    @GetMapping("/whats-hot")
    public ApiResponse<WhatsHotResponse> whatshot(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "limit", required = false, defaultValue = "5") Integer limit
    ) {
        return ApiResponse.success("WHAT'S HOT 이벤트 조회에 성공했습니다.", cachedHot(category, limit));
    }

    private WhatsHotResponse cachedHot(String category, Integer limit) {
        var key = new HotKey(category, limit);
        var entry = hotCache.get(key);
        if (entry == null || System.nanoTime() - entry.loadedAtNanos() >= HOT_CACHE_TTL_NANOS) {
            entry = hotCache.compute(key, (ignored, previous) -> {
                if (previous != null && System.nanoTime() - previous.loadedAtNanos() < HOT_CACHE_TTL_NANOS) {
                    return previous;
                }
                return new HotEntry(eventService.whatshot(category, limit), System.nanoTime());
            });
        }
        return entry.response();
    }

    @GetMapping("/weekly-ranking")
    public ApiResponse<WeeklyRankingResponse> getWeeklyRanking(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "limit", required = false, defaultValue = "5") Integer limit
    ) {
        var events = cachedHot(category, limit).events().stream()
                .map(event -> new WeeklyRankingEventResponse(event.getRank(), event.getId(), event.getName(),
                        event.getStartDate(), event.getEndDate(), event.getLocation(),
                        event.getMainImageUrl(), event.getCategory()))
                .toList();
        return ApiResponse.success("주간 이벤트 순위 조회에 성공했습니다.",
                new WeeklyRankingResponse(category, events));
    }

    @GetMapping("/{eventId}")
    public ApiResponse<EventDetailResponse> getEventTicketsById(
            @PathVariable(name = "eventId") Long eventId
    ) {
        return ApiResponse.success("이벤트 상세 조회에 성공했습니다.", eventService.getDetail(eventId));
    }
}
