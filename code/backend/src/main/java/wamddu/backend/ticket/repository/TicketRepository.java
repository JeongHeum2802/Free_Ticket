package wamddu.backend.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import wamddu.backend.ticket.domain.Ticket;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket,Long> {

    @Query("SELECT T FROM Ticket T WHERE T.event.id = :id " +
            "ORDER BY T.start_time ASC, T.price DESC")
    List<Ticket> getAllEventTickets(Long id);
}
