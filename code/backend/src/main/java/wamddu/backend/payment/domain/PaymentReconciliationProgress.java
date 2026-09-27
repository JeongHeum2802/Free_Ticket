package wamddu.backend.payment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_reconciliation_progress")
@Getter
@Setter
@NoArgsConstructor
public class PaymentReconciliationProgress {
    @Id
    private Byte id;
    private LocalDateTime coverageStartUtc;
    private LocalDateTime completedThroughUtc;
    private LocalDateTime updatedAtUtc;

    public PaymentReconciliationProgress(LocalDateTime start) {
        this.id = 1;
        this.coverageStartUtc = start;
        this.completedThroughUtc = start;
        this.updatedAtUtc = LocalDateTime.now(java.time.Clock.systemUTC());
    }
}
