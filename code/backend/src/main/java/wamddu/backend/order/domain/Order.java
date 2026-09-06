package wamddu.backend.order.domain;

import jakarta.persistence.*;
import lombok.*;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.seat.domain.Seat;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.user.domain.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ticket_id;
    private Long event_id;
    @Builder.Default
    private String paymentKey = null;

    @Column(nullable = false, unique = true, length = 64)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = true)
    @Builder.Default
    private Seat seat = null;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Builder.Default
    private LocalDateTime orderDate =  LocalDateTime.now();
    @Builder.Default
    private LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
    private Integer amount;
    private Integer quantity;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    public static Order create(String orderId, User user, Ticket ticket, Integer quantity) {
        return Order.builder()
                .orderId(orderId)
                .ticket_id(ticket.getId())
                .event_id(ticket.getEvent().getId())
                .user(user)
                .amount(ticket.getPrice() * quantity)
                .quantity(quantity)
                .build();
    }
}
