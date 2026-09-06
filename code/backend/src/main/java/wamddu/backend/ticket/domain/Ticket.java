package wamddu.backend.ticket.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import wamddu.backend.event.domain.Event;

import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String type;
    private Integer price;
    private Integer total_ticket;
    private Integer sold_ticket;
    private String description;
    private LocalDateTime bookingEndtime;
    private LocalDateTime start_time;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    public Integer getRemainingTickets() {
        return this.total_ticket - this.sold_ticket;
    }

    public Boolean isSoldOut() {
        return getRemainingTickets() <= 0;
    }

    public Boolean isAvailableBooking() {
        return !isSoldOut() && LocalDateTime.now().isBefore(this.bookingEndtime);
    }

    public void sell(Integer quantity) {
        sold_ticket += quantity;
    }
}
