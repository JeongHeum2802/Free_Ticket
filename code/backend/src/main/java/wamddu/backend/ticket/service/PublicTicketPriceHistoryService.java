package wamddu.backend.ticket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.ticket.dto.response.PublicTicketPriceHistoryResponse;
import wamddu.backend.ticket.dto.response.TicketPriceHistoryResponse.PricePoint;
import wamddu.backend.ticket.repository.TicketPriceHistoryRepository;
import wamddu.backend.ticket.repository.TicketRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicTicketPriceHistoryService {
    private final TicketRepository ticketRepository;
    private final TicketPriceHistoryRepository historyRepository;

    public PublicTicketPriceHistoryResponse getHistory(Long eventId, Long ticketId) {
        var ticket = ticketRepository.findByIdAndEventId(ticketId, eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND",
                        "해당 행사의 티켓을 찾을 수 없습니다."));
        var history = historyRepository.findAllByTicketIdOrderByChangedAtAscIdAsc(ticketId).stream()
                .map(row -> new PricePoint(row.getId(), row.getPrice(), row.getChangedAt())).toList();
        return new PublicTicketPriceHistoryResponse(ticket.getId(), ticket.getPrice(), history);
    }
}
