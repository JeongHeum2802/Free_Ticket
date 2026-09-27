package wamddu.backend.payment.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "payment_reconciliation_issue", uniqueConstraints =
        @UniqueConstraint(name = "uk_payment_recon_transaction", columnNames = {"merchant_id", "transaction_key"}),
        indexes = @Index(name = "idx_payment_recon_detected_at", columnList = "detected_at_utc"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReconciliationIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "merchant_id", nullable = false, length = 14)
    private String merchantId;
    @Column(name = "transaction_key", nullable = false, length = 64)
    private String transactionKey;
    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;
    @Column(name = "pg_payment_key", nullable = false, length = 200)
    private String pgPaymentKey;
    @Column(name = "issue_type", nullable = false, length = 40)
    private String issueType;
    @Column(name = "pg_status", nullable = false, length = 30)
    private String pgStatus;
    @Column(name = "db_order_status", length = 30)
    private String dbOrderStatus;
    @Column(name = "db_payment_status", length = 30)
    private String dbPaymentStatus;
    @Column(name = "transaction_at_utc", nullable = false)
    private LocalDateTime transactionAtUtc;
    @Column(name = "detected_at_utc", nullable = false)
    private LocalDateTime detectedAtUtc;
}
