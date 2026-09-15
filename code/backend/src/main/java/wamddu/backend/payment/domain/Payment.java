package wamddu.backend.payment.domain;

import jakarta.persistence.*;
import lombok.*;
import wamddu.backend.order.domain.Order;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false, unique = true, length = 200)
    private String paymentKey;

    @Column(nullable = false)
    private Long amount;

    private String method;

    @Column(nullable = false, length = 30)
    private String status;

    private LocalDateTime approvedAt;

    @Column(length = 500)
    private String receiptUrl;

    public static Payment createPayment(
            Order order,
            String paymentKey,
            Long amount,
            String method,
            String status,
            LocalDateTime approvedAt,
            String receiptUrl
    ) {
        return Payment.builder()
                .order(order)
                .paymentKey(paymentKey)
                .amount(amount)
                .method(method)
                .status(status)
                .approvedAt(approvedAt)
                .receiptUrl(receiptUrl)
                .build();
    }
}
