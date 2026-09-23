package wamddu.backend.ticket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.TicketRepository;
import wamddu.backend.ticket.repository.TicketPriceHistoryRepository;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PublicTicketPriceHistoryServiceTest {
    @Mock TicketRepository ticketRepository;
    @Mock TicketPriceHistoryRepository historyRepository;
    @InjectMocks PublicTicketPriceHistoryService service;

    @Test
    void rejectsTicketFromAnotherEvent() {
        given(ticketRepository.findByIdAndEventId(1L, 408L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getHistory(408L, 1L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(historyRepository);
    }

    @Test
    void returnsPublicPriceWithoutSellerMembership() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setPrice(150000);
        given(ticketRepository.findByIdAndEventId(1L, 101L)).willReturn(Optional.of(ticket));
        given(historyRepository.findAllByTicketIdOrderByChangedAtAscIdAsc(1L)).willReturn(List.of());
        var result = service.getHistory(101L, 1L);
        assertThat(result.ticketId()).isEqualTo(1L);
        assertThat(result.currentPrice()).isEqualTo(150000);
        assertThat(result.history()).isEmpty();
    }
}
