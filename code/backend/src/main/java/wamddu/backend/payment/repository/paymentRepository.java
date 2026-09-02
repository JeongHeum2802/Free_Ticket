package wamddu.backend.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import wamddu.backend.payment.domain.Payment;

@Repository
public interface paymentRepository extends JpaRepository<Payment,Long> {
}
