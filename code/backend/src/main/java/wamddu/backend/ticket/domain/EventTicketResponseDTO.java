package wamddu.backend.ticket.domain;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({
        "ticketId", "type", "price", "totalTicket", "soldTicket", "remainingTicket",
        "description", "bookingEndTime", "startTime", "soldOut", "bookingAvailable"})
public class EventTicketResponseDTO {
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

    public EventTicketResponseDTO(Ticket ticket) {
        this.ticketId = ticket.getId();
        this.type = ticket.getType();
        this.price = ticket.getPrice();
        this.totalTicket = ticket.getTotal_ticket();
        this.soldTicket = ticket.getSold_ticket();
        this.remainingTicket = ticket.getRemainingTickets();
        this.description = ticket.getDescription();
        this.bookingEndTime = ticket.getBookingEndtime();
        this.startTime = ticket.getStart_time();
        this.soldOut = ticket.isSoldOut();
        this.bookingAvailable = ticket.isAvailableBooking();
    }
}
