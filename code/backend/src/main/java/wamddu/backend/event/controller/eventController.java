package wamddu.backend.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
