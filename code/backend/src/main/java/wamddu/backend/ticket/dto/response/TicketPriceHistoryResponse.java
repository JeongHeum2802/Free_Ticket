package wamddu.backend.ticket.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record TicketPriceHistoryResponse(SellingTicketResponse ticket, List<PricePoint> history) {
    public record PricePoint(Long id, Integer price, LocalDateTime changedAt) {}
}
