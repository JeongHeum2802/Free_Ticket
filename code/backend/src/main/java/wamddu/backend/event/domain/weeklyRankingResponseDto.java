package wamddu.backend.event.domain;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({
        "rank", "id", "name", "startDate", "endDate", "location", "mainImageUrl", "category"})
public class weeklyRankingResponseDto {
    private Long rank;
    private Long id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private String location;
    private String mainImageUrl;
    private String category;
}
