package wamddu.backend.order.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface orderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderIdAndUserId(String orderId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT O FROM Order O JOIN FETCH O.user WHERE O.orderId = :orderId AND O.user.id = :userId")
    Optional<Order> findByOrderIdAndUserIdForUpdate(
            @Param("orderId") String orderId,
            @Param("userId") Long userId
    );

    @Query("SELECT COALESCE(SUM(O.quantity), 0) FROM Order O " +
            "WHERE O.ticket_id = :ticketId AND O.status IN :statuses AND O.expiresAt > :now")
    Long sumActiveQuantity(
            @Param("ticketId") Long ticketId,
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("now") LocalDateTime now
    );
}
