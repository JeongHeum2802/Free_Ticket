package wamddu.backend.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import wamddu.backend.payment.domain.PaymentReconciliationProgress;

public interface PaymentReconciliationProgressRepository extends JpaRepository<PaymentReconciliationProgress, Byte> {
}
