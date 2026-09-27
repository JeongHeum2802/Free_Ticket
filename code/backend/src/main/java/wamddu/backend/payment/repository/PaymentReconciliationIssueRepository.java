package wamddu.backend.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import wamddu.backend.payment.domain.PaymentReconciliationIssue;

public interface PaymentReconciliationIssueRepository extends JpaRepository<PaymentReconciliationIssue, Long> {
    boolean existsByMerchantIdAndTransactionKey(String merchantId, String transactionKey);
}
