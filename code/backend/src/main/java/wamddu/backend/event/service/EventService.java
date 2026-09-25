package wamddu.backend.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.dto.response.*;
import wamddu.backend.event.repository.EventRepository;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.ticket.dto.response.EventTicketResponse;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.TicketRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;

    public EventListResponse getEvents(String category) {
        log.debug("[SQL CHECK] GET /api/events START");
        try {
            if (category != null) {
                List<String> allCategories = eventRepository.findAllCategories();

                if (!allCategories.contains(category)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_CATEGORY", "유효하지 않은 이벤트 카테고리입니다.");
                }
            }

            List<EventSummaryResponse> allEvents = eventRepository.getEventsByCategory(category);
            return new EventListResponse(allEvents);
        } finally {
            log.debug("[SQL CHECK] GET /api/events END");
        }
    }

    public WhatsHotResponse whatshot(String category, Integer limit) {
        log.debug("[SQL CHECK] GET /api/events/whats-hot START");
        try {
            if (limit == null || limit < 1 || limit > 20) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "limit은 1 이상 20 이하로 입력해 주세요.");
            }

            if(category != null) {
                List<String> categories = eventRepository.findAllCategories();
                if (!categories.contains(category)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_CATEGORY", "유효하지 않은 이벤트 카테고리입니다.");
                }
            }

            List<WhatsHotEventResponse> allEvents = eventRepository.whatshot(category, limit);

            return new WhatsHotResponse(category, allEvents);
        } finally {
            log.debug("[SQL CHECK] GET /api/events/whats-hot END");
        }
    }

    public WeeklyRankingResponse weeklyRanking(String category, Integer limit) {
        log.debug("[SQL CHECK] GET /api/events/weekly-ranking START");
        try {
            if (limit == null || limit < 1 || limit > 20) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "limit은 1 이상 20 이하로 입력해 주세요.");
            }

            if(category != null) {
                List<String> categories = eventRepository.findAllCategories();
                if (!categories.contains(category)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_CATEGORY", "유효하지 않은 이벤트 카테고리입니다.");
                }
            }

            Pageable pageable = PageRequest.of(0, limit);
            List<WeeklyRankingEventResponse> allEvents = eventRepository.weeklyRanking(category, pageable);

            return new WeeklyRankingResponse(category, allEvents);
        } finally {
            log.debug("[SQL CHECK] GET /api/events/weekly-ranking END");
        }
    }

    public EventDetailResponse getDetail(Long id) {
        log.debug("[SQL CHECK] GET /api/events/{eventId} START");
        try {
            Event event = eventRepository.findById(id)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "이벤트를 찾을 수 없습니다."));

            List<Ticket> allTickets = ticketRepository.getAllEventTickets(id);

            List<EventTicketResponse> responseTickets = allTickets.stream()
                    .map(EventTicketResponse::from)
                    .toList();

            return EventDetailResponse.of(EventInfoResponse.from(event), responseTickets);
        } finally {
            log.debug("[SQL CHECK] GET /api/events/{eventId} END");
        }
    }
}
