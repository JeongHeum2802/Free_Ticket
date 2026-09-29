package wamddu.backend.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.payment.domain.Payment;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.order.orderId=:orderId")
    Optional<Payment> findByOrderOrderIdForUpdate(@Param("orderId") String orderId);

    boolean existsByPaymentKey(String paymentKey);

    @Query("SELECT P FROM Payment P JOIN FETCH P.order O " +
            "WHERE O.user.id = :userId AND O.status = :status ORDER BY O.paidAt DESC")
    List<Payment> findAllPaidByUserId(
            @Param("userId") Long userId,
            @Param("status") OrderStatus status
    );
}
