package wamddu.backend.event.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class EventsListResponseDTO<T> {
    private List<T> events;

    public static <T> EventsListResponseDTO create(List<T> events) {
        return new EventsListResponseDTO<>(events);
    }
}
