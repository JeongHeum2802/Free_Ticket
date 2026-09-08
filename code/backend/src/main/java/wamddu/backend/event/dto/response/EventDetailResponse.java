package wamddu.backend.event.dto.response;

import wamddu.backend.ticket.domain.EventTicketResponseDTO;

import java.util.List;

public record EventDetailResponse(
        EventInfoResponse event,
        List<EventTicketResponseDTO> ticketOptions
) {
    public static EventDetailResponse of(EventInfoResponse event, List<EventTicketResponseDTO> ticketOptions) {
        return new EventDetailResponse(event, ticketOptions);
    }
}
