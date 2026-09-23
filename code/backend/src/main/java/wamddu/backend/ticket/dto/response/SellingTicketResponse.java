package wamddu.backend.ticket.dto.response;

import wamddu.backend.ticket.domain.Ticket;
import java.time.LocalDateTime;

public record SellingTicketResponse(Long id, Long eventId, String eventName, String type,
        Integer price, Integer totalTicket, Integer soldTicket, LocalDateTime startTime,
        LocalDateTime bookingEndtime) {
    public static SellingTicketResponse from(Ticket ticket) {
        return new SellingTicketResponse(ticket.getId(), ticket.getEvent().getId(),
                ticket.getEvent().getName(), ticket.getType(), ticket.getPrice(),
                ticket.getTotal_ticket(), ticket.getSold_ticket(), ticket.getStart_time(),
                ticket.getBookingEndtime());
    }
}
