package wamddu.backend.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import wamddu.backend.ticket.domain.Ticket;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("SELECT T FROM Ticket T WHERE T.event.id = :id " +
            "ORDER BY T.start_time ASC, T.price DESC")
    List<Ticket> getAllEventTickets(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT T FROM Ticket T JOIN FETCH T.event WHERE T.id = :id")
    Optional<Ticket> findByIdForUpdate(Long id);

    @Query("SELECT T FROM Ticket T LEFT JOIN FETCH T.event WHERE T.id = :id")
    Optional<Ticket> findByIdWithEvent(@Param("id") Long id);
}
