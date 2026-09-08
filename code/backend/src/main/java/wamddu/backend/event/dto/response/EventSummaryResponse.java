package wamddu.backend.event.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({
        "id", "name", "startDate", "endDate", "location", "mainImageUrl", "category", "description"})
public class EventSummaryResponse {
    private Long id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private String location;
    private String mainImageUrl;
    private String category;
    private String description;
}
