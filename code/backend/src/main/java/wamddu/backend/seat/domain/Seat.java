package wamddu.backend.seat.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import wamddu.backend.event.domain.Event;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_event_seat",
                        columnNames = {"event_id", "seat"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Seat {
    @Id
    private String id;

    @Column(name = "seat", nullable = false)
    private String seat;

    @Column(name = "is_sold", nullable = false)
    private boolean isSold = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;
}
