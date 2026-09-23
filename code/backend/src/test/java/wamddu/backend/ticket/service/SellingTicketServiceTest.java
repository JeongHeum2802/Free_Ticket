package wamddu.backend.ticket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.ticket.repository.TicketRepository;
import wamddu.backend.ticket.repository.TicketPriceHistoryRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.event.domain.Event;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SellingTicketServiceTest {
    @Mock TicketRepository ticketRepository;
    @Mock TicketPriceHistoryRepository historyRepository;
    @InjectMocks SellingTicketService service;

    @Test
    void unrelatedTicketDoesNotExposeHistory() {
        given(ticketRepository.findManagedTicket(1L, 99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getHistory(99L, 1L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(historyRepository);
    }

    @Test
    void managedTicketCanHaveEmptyHistory() {
        Event event = new Event();
        event.setId(101L);
        event.setName("행사");
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setEvent(event);
        ticket.setPrice(150000);
        given(ticketRepository.findManagedTicket(1L, 10L)).willReturn(Optional.of(ticket));
        given(historyRepository.findAllByTicketIdOrderByChangedAtAscIdAsc(1L)).willReturn(List.of());

        var response = service.getHistory(10L, 1L);
        assertThat(response.ticket().eventId()).isEqualTo(101L);
        assertThat(response.history()).isEmpty();
    }
}
