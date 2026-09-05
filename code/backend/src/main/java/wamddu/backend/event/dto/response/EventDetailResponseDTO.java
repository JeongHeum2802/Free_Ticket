package wamddu.backend.event.dto.response;

import lombok.*;
import wamddu.backend.event.domain.Event;
import wamddu.backend.ticket.domain.Ticket;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EventDetailResponseDTO {

    private EventInfo event;
    private List<TicketOption> ticketOptions;

    public static EventDetailResponseDTO create(Event event,  List<Ticket> tickets) {
        return EventDetailResponseDTO.builder()
                .event(EventInfo.create(event))
                .ticketOptions(tickets.stream()
                        .map(TicketOption::create)
                        .toList())
                .build();
    }

    @Getter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class EventInfo {
        private Long id;
        private String name;
        private LocalDate startDate;
        private LocalDate endDate;
        private String location;
        private String bannerImageUrl;
        private String mainImageUrl;
        private String category;
        private String description;
        private Integer runningTime;
        private String descriptionImageUrl;

        public static EventInfo create(Event event) {
            return EventInfo.builder()
                    .id(event.getId())
                    .name(event.getName())
                    .startDate(event.getStartDate())
                    .endDate(event.getEndDate())
                    .location(event.getLocation())
                    .bannerImageUrl(event.getBannerImageUrl())
                    .mainImageUrl(event.getMainImageUrl())
                    .category(event.getCategory())
                    .description(event.getDescription())
                    .runningTime(event.getRunning_time())
                    .descriptionImageUrl(event.getDescriptionImageUrl())
                    .build();
        }
    }

    @Getter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class TicketOption {
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

        public static TicketOption create(Ticket ticket) {

            return TicketOption.builder()
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
}
