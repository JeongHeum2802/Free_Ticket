package wamddu.backend.event.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import wamddu.backend.event.domain.Event;

import java.util.List;

@Repository
public interface eventRepository extends JpaRepository<Event,Long> {
    List<Event> findAllByCategory(String category);

    @Query("SELECT DISTINCT E.category FROM Event E")
    List<String> findAllCategories();

    @Query("SELECT E FROM Event E LEFT JOIN Ticket T  ON T.event = E " +
            "WHERE (:category IS NULL OR E.category = :category) GROUP BY(E) " +
            "ORDER BY COALESCE(SUM(T.sold_ticket), 0) DESC")
    List<Event> whatshot(String category, Pageable pageable);
}
