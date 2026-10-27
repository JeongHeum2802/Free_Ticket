package wamddu.backend.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.dto.request.EventSearchCriteria;
import wamddu.backend.event.dto.response.*;
import wamddu.backend.event.repository.EventRepository;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.ticket.dto.response.EventTicketResponse;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.TicketRepository;

import java.util.List;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {
    private static final Set<String> SEARCH_CATEGORIES = Set.of(
            "concert", "musical", "play", "classic", "exhibition", "busking");

    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;

    public EventListResponse getEvents(String category, String title, String region) {
        return getEvents(new EventSearchCriteria(category, title, region, null, null, null, null));
    }

    public EventListResponse getEvents(EventSearchCriteria criteria) {
        log.debug("[SQL CHECK] GET /api/events START");
        try {
            String category = normalizeSearchValue(criteria.category());
            String title = normalizeSearchValue(criteria.title());
            String region = normalizeSearchValue(criteria.region());
            if (category != null && !SEARCH_CATEGORIES.contains(category)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_CATEGORY", "유효하지 않은 이벤트 카테고리입니다.");
            }

            // shortcut: 주소 문자열로 지역을 찾는다. 주소 약칭이나 정확한 행정구역 구분이 필요하면 지역 코드를 도입한다.
            validateTicketFilters(criteria);
            List<EventSummaryResponse> allEvents;
            if (criteria.hasTicketFilters()) {
                allEvents = eventRepository.searchEventsWithTickets(
                        category, containsPattern(title), containsPattern(region),
                        criteria.dateFrom() == null ? null : criteria.dateFrom().atStartOfDay(),
                        criteria.dateTo() == null ? null : criteria.dateTo().plusDays(1).atStartOfDay(),
                        criteria.maxPrice(), Boolean.TRUE.equals(criteria.availableOnly()), LocalDateTime.now());
            } else {
                allEvents = eventRepository.searchEvents(category, containsPattern(title), containsPattern(region));
            }
            return new EventListResponse(allEvents);
        } finally {
            log.debug("[SQL CHECK] GET /api/events END");
        }
    }

    private void validateTicketFilters(EventSearchCriteria criteria) {
        if (criteria.maxPrice() != null && criteria.maxPrice() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MAX_PRICE", "최대 가격은 0원 이상이어야 합니다.");
        }
        // Keep dates within the database calendar range and allow an exclusive next-day upper bound.
        LocalDate minDate = LocalDate.of(1000, 1, 1);
        LocalDate maxDate = LocalDate.of(9999, 12, 30);
        if ((criteria.dateFrom() != null && (criteria.dateFrom().isBefore(minDate) || criteria.dateFrom().isAfter(maxDate)))
                || (criteria.dateTo() != null && (criteria.dateTo().isBefore(minDate) || criteria.dateTo().isAfter(maxDate)))
                || (criteria.dateFrom() != null && criteria.dateTo() != null && criteria.dateFrom().isAfter(criteria.dateTo()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "유효한 날짜 범위를 입력해 주세요. 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    private String normalizeSearchValue(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.strip();
        if (trimmed.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_VALUE", "검색 조건은 100자 이하로 입력해 주세요.");
        }
        return trimmed;
    }

    private String containsPattern(String value) {
        if (value == null) return null;
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
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
