package wamddu.backend.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import wamddu.backend.order.domain.Order;

import java.time.LocalDateTime;

@Entity
@Setter
@Getter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String paymentKey;
    private String method;
    private String status;
    private LocalDateTime approvedAt;
    private String receiptUrl;
}
