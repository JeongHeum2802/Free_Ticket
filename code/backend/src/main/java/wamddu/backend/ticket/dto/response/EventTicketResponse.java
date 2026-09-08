package wamddu.backend.ticket.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import wamddu.backend.ticket.domain.Ticket;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({
        "ticketId", "type", "price", "totalTicket", "soldTicket", "remainingTicket",
        "description", "bookingEndTime", "startTime", "soldOut", "bookingAvailable"})
public class EventTicketResponse {
    private Long ticketId;
    private String type;
    private Integer price;
    private Integer totalTicket;
    private Integer soldTicket;
    private Integer remainingTicket;
    private String description;
    private LocalDateTime bookingEndTime;
    private LocalDateTime startTime;
    private Boolean soldOut;
    private Boolean bookingAvailable;

    public static EventTicketResponse from(Ticket ticket) {
        if (ticket == null) {
            return null;
        }
        return EventTicketResponse.builder()
                .ticketId(ticket.getId())
                .type(ticket.getType())
                .price(ticket.getPrice())
                .totalTicket(ticket.getTotal_ticket())
                .soldTicket(ticket.getSold_ticket())
                .remainingTicket(ticket.getRemainingTickets())
                .description(ticket.getDescription())
                .bookingEndTime(ticket.getBookingEndtime())
                .startTime(ticket.getStart_time())
                .soldOut(ticket.isSoldOut())
                .bookingAvailable(ticket.isAvailableBooking())
                .build();
    }
}
