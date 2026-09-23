package wamddu.backend.ticket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.ticket.dto.response.SellingTicketResponse;
import wamddu.backend.ticket.dto.response.TicketPriceHistoryResponse;
import wamddu.backend.ticket.repository.TicketRepository;
import wamddu.backend.ticket.repository.TicketPriceHistoryRepository;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellingTicketService {
    private final TicketRepository ticketRepository;
    private final TicketPriceHistoryRepository historyRepository;

    public List<SellingTicketResponse> getMyTickets(Long userId) {
        return ticketRepository.findAllManagedBy(userId).stream().map(SellingTicketResponse::from).toList();
    }

    public TicketPriceHistoryResponse getHistory(Long userId, Long ticketId) {
        var ticket = ticketRepository.findManagedTicket(ticketId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND",
                        "관리할 수 있는 티켓을 찾을 수 없습니다."));
        var history = historyRepository.findAllByTicketIdOrderByChangedAtAscIdAsc(ticketId).stream()
                .map(row -> new TicketPriceHistoryResponse.PricePoint(row.getId(), row.getPrice(), row.getChangedAt()))
                .toList();
        return new TicketPriceHistoryResponse(SellingTicketResponse.from(ticket), history);
    }
}
