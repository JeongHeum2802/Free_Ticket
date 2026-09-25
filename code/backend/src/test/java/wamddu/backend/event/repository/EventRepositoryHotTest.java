package wamddu.backend.event.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.event.domain.Event;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.TicketRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class EventRepositoryHotTest {
    @Autowired EventRepository events;
    @Autowired TicketRepository tickets;
    @Autowired EntityManager entityManager;

    @Test
    void hotUsesEndDateThenIdToBreakSalesTiesWithoutChangingDenseRank() {
        Event late = event("late", LocalDate.of(2026, 12, 31));
        Event early = event("early", LocalDate.of(2026, 10, 1));
        Event sameDate = event("same date", LocalDate.of(2026, 10, 1));
        event("no ticket", LocalDate.of(2026, 9, 1));
        ticket(late, 10);
        ticket(early, 10);
        ticket(sameDate, 10);
        entityManager.flush();
        entityManager.clear();

        var hot = events.whatshot(null, 2);

        assertThat(hot).extracting("id").containsExactly(early.getId(), sameDate.getId());
        assertThat(hot).extracting("rank").containsExactly(1L, 1L);
        assertThat(hot).extracting("name").containsExactly("early", "same date");
    }

    private Event event(String name, LocalDate endDate) {
        Event event = new Event();
        event.setName(name);
        event.setStartDate(LocalDate.of(2026, 9, 1));
        event.setEndDate(endDate);
        event.setLocation("Seoul");
        event.setBannerImageUrl("banner");
        event.setMainImageUrl("main");
        event.setCategory("musical");
        return events.save(event);
    }

    private void ticket(Event event, int sold) {
        Ticket ticket = new Ticket();
        ticket.setEvent(event);
        ticket.setType("general");
        ticket.setSold_ticket(sold);
        tickets.save(ticket);
    }
}
