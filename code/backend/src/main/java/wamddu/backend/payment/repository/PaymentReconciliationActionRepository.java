package wamddu.backend.payment.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import wamddu.backend.payment.domain.PaymentReconciliationAction;

public interface PaymentReconciliationActionRepository extends JpaRepository<PaymentReconciliationAction, Long> {
    List<PaymentReconciliationAction> findTop50ByIssueIdOrderByIdDesc(long issueId);

    @Query("select count(a) > 0 from PaymentReconciliationAction a join a.issue i " +
            "where i.orderId=:orderId and i.pgPaymentKey=:paymentKey and i.merchantId=:merchantId " +
            "and a.action='APPLY_FULL_CANCELLATION' and a.result='SUCCESS'")
    boolean hasAppliedCancellation(@Param("orderId") String orderId, @Param("paymentKey") String paymentKey,
                                   @Param("merchantId") String merchantId);
}
