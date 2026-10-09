package wamddu.backend.payment.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "payment_reconciliation_action")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReconciliationAction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private PaymentReconciliationIssue issue;
    @Column(nullable = false)
    private Long actorId;
    @Column(nullable = false, length = 40)
    private String action;
    @Column(nullable = false, length = 20)
    private String result;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(length = 80)
    private String errorCode;
    @Column(nullable = false)
    private LocalDateTime occurredAtUtc;
    @Lob @Column(columnDefinition = "longtext")
    private String pgSnapshotJson;
    @Column(length = 30)
    private String beforeOrderStatus;
    @Column(length = 30)
    private String beforePaymentStatus;
    @Column(length = 30)
    private String afterOrderStatus;
    @Column(length = 30)
    private String afterPaymentStatus;
    private Integer soldBefore;
    private Integer soldAfter;
}
