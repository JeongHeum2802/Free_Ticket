package wamddu.backend.event.dto.response;

import wamddu.backend.ticket.dto.response.EventTicketResponse;

import java.util.List;

public record EventDetailResponse(
        EventInfoResponse event,
        List<EventTicketResponse> ticketOptions
) {
    public static EventDetailResponse of(EventInfoResponse event, List<EventTicketResponse> ticketOptions) {
        return new EventDetailResponse(event, ticketOptions);
    }
}
