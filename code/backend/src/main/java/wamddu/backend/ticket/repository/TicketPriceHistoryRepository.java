package wamddu.backend.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import wamddu.backend.ticket.domain.TicketPriceHistory;
import java.util.List;

public interface TicketPriceHistoryRepository extends JpaRepository<TicketPriceHistory, Long> {
    List<TicketPriceHistory> findAllByTicketIdOrderByChangedAtAscIdAsc(Long ticketId);
}
