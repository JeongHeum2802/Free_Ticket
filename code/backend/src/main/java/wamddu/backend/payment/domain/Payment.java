package wamddu.backend.payment.domain;

import jakarta.persistence.*;
import lombok.*;
import wamddu.backend.order.domain.Order;
import wamddu.backend.payment.dto.response.ConfirmResponseDTO;

import java.time.OffsetDateTime;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String paymentKey;
    private String method;
    private String status;
    private OffsetDateTime approvedAt;
    private String receiptUrl;

    @OneToOne(mappedBy = "payment", fetch = FetchType.LAZY)
    private Order order;

    public static Payment create(ConfirmResponseDTO response) {
        return Payment.builder()
                .paymentKey(response.getPaymentKey())
                .method(response.getMethod())
                .status(response.getStatus())
                .approvedAt(response.getApprovedAt())
                .receiptUrl(response.getReceiptUrl())
                .build();
    }
}
