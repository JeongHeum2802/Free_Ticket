package wamddu.backend.event.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.dto.request.EventSearchCriteria;
import wamddu.backend.event.dto.response.EventSummaryResponse;
import wamddu.backend.event.service.EventService;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.ticket.domain.Ticket;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class EventPaginationTest {
    private static final EventSearchCriteria ALL = new EventSearchCriteria(null, null, null, null, null, null, null);

    @Autowired EntityManager em;
    @Autowired EventService service;

    @Test
    void latestPagesDoNotSkipTheExtraLookaheadRow() {
        var events = events(65);

        var first = service.getEventPage(ALL, 0, 30, "latest");
        var second = service.getEventPage(ALL, 1, 30, "latest");
        var last = service.getEventPage(ALL, 2, 30, "latest");
        var empty = service.getEventPage(ALL, 3, 30, "latest");

        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(30);
        assertThat(first.events()).hasSize(30).extracting(EventSummaryResponse::getId)
                .isSortedAccordingTo(Comparator.reverseOrder())
                .startsWith(events.get(64).getId()).endsWith(events.get(35).getId());
        assertThat(second.events()).hasSize(30).extracting(EventSummaryResponse::getId)
                .isSortedAccordingTo(Comparator.reverseOrder())
                .startsWith(events.get(34).getId()).endsWith(events.get(5).getId());
        assertThat(last.events()).extracting(EventSummaryResponse::getId)
                .containsExactly(events.get(4).getId(), events.get(3).getId(), events.get(2).getId(),
                        events.get(1).getId(), events.get(0).getId());
        assertThat(first.hasNext()).isTrue();
        assertThat(second.hasNext()).isTrue();
        assertThat(last.hasNext()).isFalse();
        assertThat(empty.events()).isEmpty();
        assertThat(empty.hasNext()).isFalse();
        assertThat(service.getEvents(ALL).events()).hasSize(65);
    }

    @Test
    void exactFullLastPageHasNoNextPage() {
        events(60);

        var last = service.getEventPage(ALL, 1, 30, "latest");

        assertThat(last.events()).hasSize(30);
        assertThat(last.hasNext()).isFalse();
        assertThat(service.getEventPage(ALL, 2, 30, "latest").events()).isEmpty();
    }

    @Test
    void closingSoonSortsTheWholeResultAndBreaksDateTiesByNewestId() {
        var earlyOlder = event("early older", "musical", LocalDate.of(2030, 2, 1));
        var earlyNewer = event("early newer", "musical", LocalDate.of(2030, 2, 1));
        var middle = event("middle", "musical", LocalDate.of(2030, 5, 1));
        var later = events(35);

        assertThat(service.getEventPage(ALL, 0, 5, "latest").events())
                .extracting(EventSummaryResponse::getId).doesNotContain(earlyOlder.getId(), earlyNewer.getId());
        var closingSoon = service.getEventPage(ALL, 0, 5, "closingSoon");
        assertThat(closingSoon.events()).extracting(EventSummaryResponse::getId)
                .containsExactly(earlyNewer.getId(), earlyOlder.getId(), middle.getId(),
                        later.get(34).getId(), later.get(33).getId());
        assertThat(closingSoon.hasNext()).isTrue();
        assertThat(service.getEventPage(ALL, 1, 1, "closingSoon").events())
                .extracting(EventSummaryResponse::getId).containsExactly(earlyOlder.getId());
    }

    @Test
    void pagingPreservesCategoryLiteralSearchAndSameTicketFiltersWithoutDuplicates() {
        var matches = new ArrayList<Event>();
        for (int i = 0; i < 31; i++) {
            var match = event("페이지%_" + i, "musical", LocalDate.of(2030, 12, 31));
            ticket(match, "2030-05-04T00:00:00", 50000);
            ticket(match, "2030-05-04T23:59:59", 40000);
            matches.add(match);
        }
        var split = event("페이지%_split", "musical", LocalDate.of(2030, 12, 31));
        ticket(split, "2030-05-04T12:00:00", 60000);
        ticket(split, "2030-05-05T00:00:00", 30000);
        ticket(event("페이지%_category", "concert", LocalDate.of(2030, 12, 31)), "2030-05-04T12:00:00", 10000);
        ticket(event("페이지wildcards", "musical", LocalDate.of(2030, 12, 31)), "2030-05-04T12:00:00", 10000);
        var closed = ticket(event("페이지%_closed", "musical", LocalDate.of(2030, 12, 31)), "2030-05-04T12:00:00", 10000);
        closed.setBookingEndtime(LocalDateTime.now().minusDays(1));
        event("페이지%_ticketless", "musical", LocalDate.of(2030, 12, 31));
        var criteria = new EventSearchCriteria("musical", "페이지%_", "서울", LocalDate.of(2030, 5, 4),
                LocalDate.of(2030, 5, 4), 50000, true);

        var first = service.getEventPage(criteria, 0, 30, "latest");
        var second = service.getEventPage(criteria, 1, 30, "latest");

        assertThat(first.events()).hasSize(30).extracting(EventSummaryResponse::getId)
                .isSortedAccordingTo(Comparator.reverseOrder())
                .startsWith(matches.get(30).getId()).endsWith(matches.get(1).getId());
        assertThat(first.hasNext()).isTrue();
        assertThat(second.events()).extracting(EventSummaryResponse::getId).containsExactly(matches.get(0).getId());
        assertThat(second.hasNext()).isFalse();
        assertThat(service.getEventPage(criteria, 0, 1, "closingSoon").events())
                .extracting(EventSummaryResponse::getId).containsExactly(matches.get(30).getId());
    }

    @Test
    void rejectsInvalidPagingBeforeQueryingAndAcceptsTheMaximumOffset() {
        assertInvalidPage(-1, 30, "latest");
        assertInvalidPage(0, 0, "latest");
        assertInvalidPage(0, 101, "latest");
        assertInvalidPage(0, 30, "unknown");
        assertInvalidPage(0, 30, null);
        assertInvalidPage(Integer.MAX_VALUE, 2, "latest");
        assertThatCode(() -> EventService.validatePagination(Integer.MAX_VALUE, 1, "latest")).doesNotThrowAnyException();
        assertThatCode(() -> EventService.validatePagination(0, 100, "closingSoon")).doesNotThrowAnyException();
    }

    @Test
    void pagedSearchRetainsExistingSearchValidation() {
        assertThatThrownBy(() -> service.getEventPage(new EventSearchCriteria("unknown", null, null, null, null, null, null), 0, 30, "latest"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_EVENT_CATEGORY");
        assertThatThrownBy(() -> service.getEventPage(new EventSearchCriteria(null, null, null, null, null, -1, null), 0, 30, "latest"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_MAX_PRICE");
        assertThatThrownBy(() -> service.getEventPage(new EventSearchCriteria(null, null, null, LocalDate.of(2030, 5, 5), LocalDate.of(2030, 5, 4), null, null), 0, 30, "latest"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_DATE_RANGE");
    }

    private void assertInvalidPage(int page, int size, String sort) {
        assertThatThrownBy(() -> service.getEventPage(ALL, page, size, sort))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private List<Event> events(int count) {
        var events = new ArrayList<Event>();
        for (int i = 0; i < count; i++) {
            events.add(event("event " + i, "musical", LocalDate.of(2030, 12, 31)));
        }
        return events;
    }

    private Event event(String name, String category, LocalDate endDate) {
        var event = new Event();
        event.setName(name);
        event.setCategory(category);
        event.setLocation("서울");
        event.setStartDate(LocalDate.of(2030, 1, 1));
        event.setEndDate(endDate);
        event.setBannerImageUrl("banner");
        event.setMainImageUrl("main");
        em.persist(event);
        return event;
    }

    private Ticket ticket(Event event, String start, int price) {
        var ticket = new Ticket();
        ticket.setEvent(event);
        ticket.setType("general");
        ticket.setStart_time(LocalDateTime.parse(start));
        ticket.setPrice(price);
        ticket.setTotal_ticket(10);
        ticket.setSold_ticket(0);
        ticket.setBookingEndtime(LocalDateTime.now().plusYears(10));
        em.persist(ticket);
        return ticket;
    }
}
