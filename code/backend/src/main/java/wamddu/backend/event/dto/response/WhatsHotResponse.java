package wamddu.backend.event.dto.response;

import java.util.List;

public record WhatsHotResponse(String category, List<WhatsHotEventResponse> events) {
}
