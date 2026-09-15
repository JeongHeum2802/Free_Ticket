package wamddu.backend.order.domain;

import jakarta.persistence.*;
import lombok.*;
import wamddu.backend.user.domain.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ticket_id;
    private Long event_id;

    @Column(nullable = false, unique = true, length = 64)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    @Builder.Default
    private LocalDateTime orderDate = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer unitPrice;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    private LocalDateTime paidAt;

    @Column(nullable = false, unique = true, length = 36)
    private String idempotencyKey;

    public static Order createPendingOrder(
            String orderId,
            User user,
            Long ticketId,
            Long eventId,
            int quantity,
            int unitPrice,
            String idempotencyKey,
            int paymentWindowMinutes
    ) {
        LocalDateTime now = LocalDateTime.now();
        return Order.builder()
                .orderId(orderId)
                .user(user)
                .ticket_id(ticketId)
                .event_id(eventId)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .totalAmount((long) unitPrice * quantity)
                .status(OrderStatus.PENDING)
                .orderDate(now)
                .expiresAt(now.plusMinutes(paymentWindowMinutes))
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
