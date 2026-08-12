package wamddu.backend.event.domain;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@JsonPropertyOrder({
        "rank", "id", "name", "startDate", "endDate", "location", "bannerImageUrl",
        "mainImageUrl, category"})
@AllArgsConstructor
@Getter
public class whatshotResponseDto {
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
