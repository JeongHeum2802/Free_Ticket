package wamddu.backend.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import wamddu.backend.order.domain.Order;

import java.util.List;

@Repository
public interface orderRepository extends JpaRepository<Order, Long> {
    Order findByOrderId(String OrderId);
    List<Order> findByUserId(Long userId);
}
