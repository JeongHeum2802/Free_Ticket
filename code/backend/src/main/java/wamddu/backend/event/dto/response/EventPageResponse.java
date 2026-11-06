package wamddu.backend.event.dto.response;

import java.util.List;

public record EventPageResponse(List<EventSummaryResponse> events, int page, int size, boolean hasNext) {
}
