package wamddu.backend.event.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.repository.eventRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class eventService {

    private final eventRepository eventRepository;

    public ResponseEntity<Map<String, Object>> getEvents(String category) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> events = new LinkedHashMap<>();

        List<String> categories = eventRepository.findAllCategories();

        if(category == null) {
            List<Event> allEvents = eventRepository.findAll();

            if(allEvents.isEmpty()) {
                response.put("message", "조회된 이벤트가 없습니다.");
            }

            else {
                response.put("message", "이벤트 목록 조회에 성공했습니다.");
            }

            events.put("events", allEvents);
            response.put("data", events);

            return ResponseEntity.status(HttpStatus.OK).body(response);
        }

        else {
            List<Event> allEvents =  eventRepository.findAllByCategory(category);

            if(!categories.contains(category)) {
                response.put("code", "INVALID_EVENT_CATEGORY");
                response.put("message", "유효하지 않은 이벤트 카테고리입니다.");

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            if(allEvents.isEmpty()) {
                response.put("message", "조회된 이벤트가 없습니다.");
            }

            else {
                response.put("message", "이벤트 목록 조회에 성공했습니다.");
            }

            events.put("events", allEvents);
            response.put("data", events);

            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    public ResponseEntity<Map<String, Object>> whatshot(String category, Integer limit) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> events = new LinkedHashMap<>();

        //잘못된 limit
        if(limit < 1 || limit > 20){
            response.put("code", "INVALID_LIMIT");
            response.put("message", "limit은 1 이상 20 이하로 입력해 주세요.");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        //잘못된 카테고리
        List<String> categories = eventRepository.findAllCategories();
        if(category != null && !categories.contains(category)) {
            response.put("code", "INVALID_EVENT_CATEGORY");
            response.put("message", "유효하지 않은 이벤트 카테고리입니다.");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Pageable pageable = PageRequest.of(0, limit);
        List<Event> allEvents = eventRepository.whatshot(category, pageable);

        events.put("category", category);
        events.put("events", allEvents);

        response.put("message", "WHAT'S HOT 이벤트 조회에 성공했습니다.");
        response.put("data", events);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
