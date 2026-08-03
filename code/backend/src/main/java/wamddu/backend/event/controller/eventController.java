package wamddu.backend.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wamddu.backend.event.service.eventService;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class eventController {

    private final eventService eventService;

    @GetMapping("/api/events")
    public ResponseEntity<Map<String, Object>> getEvents(
            @RequestParam(name = "category", required = false) String category
    ) {
        return eventService.getEvents(category);
    }

    @GetMapping("/api/events/whats-hot")
    public ResponseEntity<Map<String, Object>> whatshot(
            @RequestParam(name = "category" , required = false) String category,
            @RequestParam(name = "limit", required = false, defaultValue = "5") Integer limit
    ) {
        return eventService.whatshot(category, limit);
    }

    @GetMapping("/api/events/weekly-ranking")
    public ResponseEntity<Map<String, Object>> getWeeklyRanking(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "limit", required = false, defaultValue = "5") Integer limit
    ){
        return eventService.weeklyRanking(category, limit);
    }

    @GetMapping("/api/events/{eventId}")
    public ResponseEntity<Map<String, Object>> getEventTicketsById(
            @PathVariable(name = "eventId") Long eventId
    ) {
        return eventService.getDetail(eventId);
    }
}
