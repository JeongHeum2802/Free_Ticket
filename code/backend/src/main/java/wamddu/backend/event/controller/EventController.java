package wamddu.backend.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import wamddu.backend.event.dto.response.EventDetailResponse;
import wamddu.backend.event.dto.response.EventListResponse;
import wamddu.backend.event.dto.response.WeeklyRankingResponse;
import wamddu.backend.event.dto.response.WhatsHotResponse;
import wamddu.backend.event.service.EventService;
import wamddu.backend.global.response.ApiResponse;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ApiResponse<EventListResponse> getEvents(
            @RequestParam(name = "category", required = false) String category
    ) {
        EventListResponse response = eventService.getEvents(category);
        String message = response.events().isEmpty() ? "조회된 이벤트가 없습니다." : "이벤트 목록 조회에 성공했습니다.";
        return ApiResponse.success(message, response);
    }

    @GetMapping("/whats-hot")
    public ApiResponse<WhatsHotResponse> whatshot(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "limit", required = false, defaultValue = "5") Integer limit
    ) {
        return ApiResponse.success("WHAT'S HOT 이벤트 조회에 성공했습니다.", eventService.whatshot(category, limit));
    }

    @GetMapping("/weekly-ranking")
    public ApiResponse<WeeklyRankingResponse> getWeeklyRanking(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "limit", required = false, defaultValue = "5") Integer limit
    ) {
        return ApiResponse.success("주간 이벤트 순위 조회에 성공했습니다.", eventService.weeklyRanking(category, limit));
    }

    @GetMapping("/{eventId}")
    public ApiResponse<EventDetailResponse> getEventTicketsById(
            @PathVariable(name = "eventId") Long eventId
    ) {
        return ApiResponse.success("이벤트 상세 조회에 성공했습니다.", eventService.getDetail(eventId));
    }
}
