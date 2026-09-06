package wamddu.backend.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wamddu.backend.event.dto.response.*;
import wamddu.backend.event.service.EventService;
import wamddu.backend.global.response.SuccessResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping("/api/events")
    public SuccessResponse<EventsListResponseDTO> getEvents(
            @RequestParam(name = "category", required = false) String category
    ) {
        List<GetEventsResponseDto> responses =  eventService.getEvents(category);

        if(responses.isEmpty())
            return SuccessResponse.success("조회된 이벤트가 없습니다.", EventsListResponseDTO.create(responses));

        else
            return SuccessResponse.success("이벤트 목록 조회에 성공했습니다.", EventsListResponseDTO.create(responses));
    }

    @GetMapping("/api/events/whats-hot")
    public SuccessResponse<EventsListResponseDTO> whatshot(
            @RequestParam(name = "category" , required = false) String category,
            @RequestParam(name = "limit", required = false, defaultValue = "5") Integer limit
    ) {
        List<WhatsHotResponseDto> responses = eventService.whatshot(category, limit);

        return SuccessResponse.success("WHAT'S HOT 이벤트 조회에 성공했습니다.", EventsListResponseDTO.create(responses));
    }

    @GetMapping("/api/events/weekly-ranking")
    public SuccessResponse<EventsListResponseDTO> getWeeklyRanking(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "limit", required = false, defaultValue = "5") Integer limit
    ){
        List<WeeklyRankingResponseDto> responses = eventService.weeklyRanking(category, limit);

        return SuccessResponse.success("주간 이벤트 순위 조회에 성공했습니다.", EventsListResponseDTO.create(responses));
    }

    @GetMapping("/api/events/{eventId}")
    public SuccessResponse<EventDetailResponseDTO> getEventTicketsById(
            @PathVariable(name = "eventId") Long eventId
    ) {
        EventDetailResponseDTO response = eventService.getDetail(eventId);

        return SuccessResponse.success("이벤트 상세 조회에 성공했습니다.", response);
    }
}
