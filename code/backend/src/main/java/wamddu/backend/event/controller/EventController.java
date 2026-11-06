package wamddu.backend.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import wamddu.backend.event.dto.request.EventSearchCriteria;
import wamddu.backend.event.dto.response.EventDetailResponse;
import wamddu.backend.event.dto.response.EventPageResponse;
import wamddu.backend.event.dto.response.WeeklyRankingEventResponse;
import wamddu.backend.event.dto.response.WeeklyRankingResponse;
import wamddu.backend.event.dto.response.WhatsHotResponse;
import wamddu.backend.event.service.EventService;
import wamddu.backend.event.service.EventListCache;
import wamddu.backend.global.response.ApiResponse;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Set;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final EventListCache eventListCache;
    private static final Set<String> CACHE_CATEGORIES = Set.of("concert", "musical", "play", "classic", "exhibition", "busking");
    private static final long HOT_CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(5);
    // ponytail: 서버별 캐시다. 여러 서버에서 같은 갱신 시점이 필요해지면 공유 캐시를 검토한다.
    private final ConcurrentHashMap<HotKey, HotEntry> hotCache = new ConcurrentHashMap<>();

    private record HotKey(String category, Integer limit) {}
    private record HotEntry(WhatsHotResponse response, long loadedAtNanos) {}

    @GetMapping
    public ResponseEntity<?> getEvents(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "region", required = false) String region,
            @RequestParam(name = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(name = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(name = "maxPrice", required = false) Integer maxPrice,
            @RequestParam(name = "availableOnly", required = false) Boolean availableOnly,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "30") int size,
            @RequestParam(name = "sort", defaultValue = "latest") String sort
    ) {
        EventService.validatePagination(page, size, sort);
        var criteria = new EventSearchCriteria(category, title, region, dateFrom, dateTo, maxPrice, availableOnly);
        if ((category == null || CACHE_CATEGORIES.contains(category)) && title == null && region == null && dateFrom == null && dateTo == null
                && maxPrice == null && availableOnly == null && eventListCache.isEnabled()) {
            String field = (category == null ? "all" : category) + ":" + sort + ":" + page + ":" + size;
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(eventListCache.getOrLoad(field, () -> loadEvents(criteria, page, size, sort)));
        }
        return ResponseEntity.ok(loadEvents(criteria, page, size, sort));
    }

    private ApiResponse<EventPageResponse> loadEvents(EventSearchCriteria criteria, int page, int size, String sort) {
        EventPageResponse response = eventService.getEventPage(criteria, page, size, sort);
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
