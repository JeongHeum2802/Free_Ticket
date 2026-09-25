package wamddu.backend.event.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.dto.response.EventSummaryResponse;
import wamddu.backend.event.dto.response.WeeklyRankingEventResponse;
import wamddu.backend.event.dto.response.WhatsHotEventResponse;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event,Long> {

    @Query("SELECT DISTINCT E.category FROM Event E")
    List<String> findAllCategories();

    @Query("SELECT new wamddu.backend.event.dto.response.EventSummaryResponse(" +
            "E.id, E.name, E.startDate, E.endDate, E.location, E.mainImageUrl, E.category, E.description ) " +
            "FROM Event E " +
            "WHERE (:category IS NULL OR :category = E.category)")
    List<EventSummaryResponse> getEventsByCategory(String category);

    @Query("SELECT new wamddu.backend.event.dto.response.WhatsHotEventResponse(" +
            "R.rankValue, E.id, E.name, E.startDate, E.endDate, E.location, E.bannerImageUrl, E.mainImageUrl, E.category) " +
            "FROM (SELECT E.id AS eventId, E.endDate AS eventEndDate, " +
            "DENSE_RANK() OVER (ORDER BY COALESCE(SUM(T.sold_ticket), 0) DESC) AS rankValue, " +
            "COALESCE(SUM(T.sold_ticket), 0) AS soldCount " +
            "FROM Event E LEFT JOIN Ticket T ON T.event = E " +
            "WHERE (:category IS NULL OR :category = E.category) " +
            "GROUP BY E.id, E.endDate " +
            "ORDER BY soldCount DESC, E.endDate ASC, E.id ASC LIMIT :limit) R " +
            "JOIN Event E ON E.id = R.eventId " +
            "ORDER BY R.soldCount DESC, R.eventEndDate ASC, R.eventId ASC")
    List<WhatsHotEventResponse> whatshot(String category, Integer limit);

    @Query("SELECT new wamddu.backend.event.dto.response.WeeklyRankingEventResponse(" +
            "DENSE_RANK () OVER (ORDER BY COALESCE(SUM(T.sold_ticket), 0) DESC), " +
            "E.id, E.name, E.startDate, E.endDate, E.location, E.mainImageUrl, E.category ) " +
            "FROM Event E LEFT JOIN Ticket T ON T.event = E " +
            "WHERE (:category IS NULL OR :category = E.category) " +
            "GROUP BY E " +
            "ORDER BY COALESCE(SUM(T.sold_ticket), 0) DESC")
    List<WeeklyRankingEventResponse> weeklyRanking(String category, Pageable pageable);
}
