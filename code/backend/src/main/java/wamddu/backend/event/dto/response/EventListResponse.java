package wamddu.backend.event.dto.response;

import java.util.List;

public record EventListResponse(List<EventSummaryResponse> events) {
}
