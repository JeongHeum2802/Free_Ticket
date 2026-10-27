package wamddu.backend.event.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.dto.request.EventSearchCriteria;
import wamddu.backend.event.service.EventService;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.user.domain.User;
import wamddu.backend.event.controller.EventController;
import wamddu.backend.global.exception.ApiExceptionHandler;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class EventSearchTest {
    @Autowired EntityManager em;
    @Autowired EventService service;

    @Test
    void legacySearchKeepsTicketlessEventsAndLiteralWildcards() {
        Event older = event("검색%_공연");
        Event newer = event("검색%_새공연");
        event("검색다른공연");
        assertThat(service.getEvents("musical", "검색%_", "서울").events())
                .extracting("id").containsExactly(newer.getId(), older.getId());
        assertThat(service.getEvents(new EventSearchCriteria("musical", "검색%_", "서울", null, null, null, false)).events())
                .extracting("id").containsExactly(newer.getId(), older.getId());
    }

    @Test
    void dateAndPriceMustMatchSameTicketWithoutDuplicates() {
        Event match = event("match");
        ticket(match, "2030-05-04T00:00:00", 50000);
        ticket(match, "2030-05-04T23:59:59", 40000);
        Event split = event("split");
        ticket(split, "2030-05-04T12:00:00", 60000);
        ticket(split, "2030-05-05T00:00:00", 30000);
        event("no session");
        var criteria = new EventSearchCriteria(null, null, "서울", LocalDate.of(2030, 5, 4), LocalDate.of(2030, 5, 4), 50000, null);
        assertThat(service.getEvents(criteria).events()).extracting("id").containsExactly(match.getId());
    }

    @Test
    void availableOnlyExcludesClosedSoldOutAndInvalidTickets() {
        Event available = event("available");
        ticket(available, "2030-05-04T12:00:00", 0);
        ticket(event("closed"), "2030-05-04T12:00:00", 10000).setBookingEndtime(LocalDateTime.now().minusDays(1));
        ticket(event("sold out"), "2030-05-04T12:00:00", 10000).setSold_ticket(10);
        ticket(event("invalid"), "2030-05-04T12:00:00", -1);
        assertThat(service.getEvents(new EventSearchCriteria(null, null, null, null, null, null, true)).events())
                .extracting("id").containsExactly(available.getId());
    }

    @Test
    void supportsOneSidedDateBounds() {
        Event before = event("before");
        ticket(before, "2030-05-03T23:59:59", 1);
        Event after = event("after");
        ticket(after, "2030-05-04T00:00:00", 1);
        assertThat(service.getEvents(new EventSearchCriteria(null, null, null, LocalDate.of(2030, 5, 4), null, null, null)).events())
                .extracting("id").containsExactly(after.getId());
        assertThat(service.getEvents(new EventSearchCriteria(null, null, null, null, LocalDate.of(2030, 5, 3), null, null)).events())
                .extracting("id").containsExactly(before.getId());
    }

    @Test
    void rejectsInvalidBounds() {
        assertThatThrownBy(() -> service.getEvents(new EventSearchCriteria(null, null, null, null, null, -1, null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_MAX_PRICE");
        assertThatThrownBy(() -> service.getEvents(new EventSearchCriteria(null, null, null, LocalDate.of(2030, 5, 5), LocalDate.of(2030, 5, 4), null, null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_DATE_RANGE");
        assertThatThrownBy(() -> service.getEvents(new EventSearchCriteria(null, null, null, null, LocalDate.MAX, null, null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void availabilityAccountsForPendingAndRecoveryHoldsWithoutDoubleCountingPaidCancellations() {
        Order pending = hold(ticket(event("pending"), "2030-05-04T12:00:00", 100), OrderStatus.PENDING);
        pending.setExpiresAt(LocalDateTime.now().plusHours(1));
        hold(ticket(event("confirming"), "2030-05-04T12:00:00", 100), OrderStatus.CONFIRMING);
        hold(ticket(event("canceling unpaid"), "2030-05-04T12:00:00", 100), OrderStatus.CANCELING);
        Event expired = event("expired pending");
        hold(ticket(expired, "2030-05-04T12:00:00", 100), OrderStatus.PENDING);
        Event paidCancellation = event("paid cancellation");
        Ticket paidTicket = ticket(paidCancellation, "2030-05-04T12:00:00", 100);
        paidTicket.setTotal_ticket(11);
        paidTicket.setSold_ticket(10);
        Order canceling = hold(paidTicket, OrderStatus.CANCELING);
        em.persist(Payment.createPayment(canceling, UUID.randomUUID().toString(), 1000L, "CARD", "DONE", LocalDateTime.now(), null));

        assertThat(service.getEvents(new EventSearchCriteria(null, null, null, null, null, null, true)).events())
                .extracting("id").containsExactly(paidCancellation.getId(), expired.getId());
    }

    @Test
    void httpSearchPreservesResponseAndBindsOptionalConditions() throws Exception {
        Event match = event("matching");
        ticket(match, "2030-05-04T20:00:00", 50000);
        event("no tickets");
        var mvc = MockMvcBuilders.standaloneSetup(new EventController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/events").param("category", "musical"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events.length()").value(2));
        mvc.perform(get("/api/events").param("category", "musical").param("dateFrom", "2030-05-04")
                        .param("dateTo", "2030-05-04").param("maxPrice", "50000").param("availableOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events.length()").value(1))
                .andExpect(jsonPath("$.data.events[0].id").value(match.getId()));
        mvc.perform(get("/api/events").param("maxPrice", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/events").param("dateFrom", "not-a-date")).andExpect(status().isBadRequest());
    }

    private Order hold(Ticket ticket, OrderStatus status) {
        User user = User.builder().username("tester").password("test").email(UUID.randomUUID() + "@example.test").build();
        em.persist(user);
        Order order = Order.createPendingOrder(UUID.randomUUID().toString(), user, ticket.getId(), ticket.getEvent().getId(),
                10, 100, UUID.randomUUID().toString(), 10);
        order.setStatus(status);
        order.setExpiresAt(LocalDateTime.now().minusHours(1));
        em.persist(order);
        return order;
    }

    private Event event(String name) {
        Event event = new Event();
        event.setName(name);
        event.setCategory("musical");
        event.setLocation("서울");
        event.setStartDate(LocalDate.of(2030, 1, 1));
        event.setEndDate(LocalDate.of(2030, 12, 31));
        event.setBannerImageUrl("banner");
        event.setMainImageUrl("main");
        em.persist(event);
        return event;
    }

    private Ticket ticket(Event event, String start, int price) {
        Ticket ticket = new Ticket();
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
