package wamddu.backend.event.dto.response;

import java.util.List;

public record WeeklyRankingResponse(String category, List<WeeklyRankingEventResponse> events) {
}
