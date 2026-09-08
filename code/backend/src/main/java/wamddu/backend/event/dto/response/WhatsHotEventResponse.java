package wamddu.backend.event.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({
        "rank", "id", "name", "startDate", "endDate", "location", "bannerImageUrl",
        "mainImageUrl", "category"})
public class WhatsHotEventResponse {
    private Long rank;
    private Long id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private String location;
    private String bannerImageUrl;
    private String mainImageUrl;
    private String category;
}
