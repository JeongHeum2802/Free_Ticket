package wamddu.backend.ticket.dto.response;

import java.util.List;

public record PublicTicketPriceHistoryResponse(Long ticketId, Integer currentPrice,
        List<TicketPriceHistoryResponse.PricePoint> history) {}
