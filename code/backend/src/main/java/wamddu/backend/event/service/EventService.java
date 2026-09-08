package wamddu.backend.event.service;

import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;

    public EventListResponse getEvents(String category) {
        if (category != null) {
            List<String> allCategories = eventRepository.findAllCategories();

            if (!allCategories.contains(category)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_CATEGORY", "유효하지 않은 이벤트 카테고리입니다.");
            }
        }

        List<EventSummaryResponse> allEvents = eventRepository.getEventsByCategory(category);
        return new EventListResponse(allEvents);
    }

    public WhatsHotResponse whatshot(String category, Integer limit) {
        if (limit == null || limit < 1 || limit > 20) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "limit은 1 이상 20 이하로 입력해 주세요.");
        }

        List<String> categories = eventRepository.findAllCategories();
        if (category != null && !categories.contains(category)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_CATEGORY", "유효하지 않은 이벤트 카테고리입니다.");
        }

        Pageable pageable = PageRequest.of(0, limit);
        List<WhatsHotEventResponse> allEvents = eventRepository.whatshot(category, pageable);

        return new WhatsHotResponse(category, allEvents);
    }

    public WeeklyRankingResponse weeklyRanking(String category, Integer limit) {
        if (limit == null || limit < 1 || limit > 20) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT", "limit은 1 이상 20 이하로 입력해 주세요.");
        }

        List<String> categories = eventRepository.findAllCategories();
        if (category != null && !categories.contains(category)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_CATEGORY", "유효하지 않은 이벤트 카테고리입니다.");
        }

        Pageable pageable = PageRequest.of(0, limit);
        List<WeeklyRankingEventResponse> allEvents = eventRepository.weeklyRanking(category, pageable);

        return new WeeklyRankingResponse(category, allEvents);
    }

    public EventDetailResponse getDetail(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "이벤트를 찾을 수 없습니다."));

        List<Ticket> allTickets = ticketRepository.getAllEventTickets(id);

        List<EventTicketResponse> responseTickets = allTickets.stream()
                .map(EventTicketResponse::from)
                .toList();

        return EventDetailResponse.of(EventInfoResponse.from(event), responseTickets);
    }
}
