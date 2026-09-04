package wamddu.backend.event.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.dto.response.GetEventsResponseDto;
import wamddu.backend.event.dto.response.WeeklyRankingResponseDto;
import wamddu.backend.event.dto.response.WhatsHotResponseDto;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event,Long> {

    @Query("SELECT DISTINCT E.category FROM Event E")
    List<String> findAllCategories();

    @Query("SELECT new wamddu.backend.event.dto.response.GetEventsResponseDto(" +
            "E.id, E.name, E.startDate, E.endDate, E.location, E.mainImageUrl, E.category, E.description ) " +
            "FROM Event E " +
            "WHERE (:category IS NULL OR :category = E.category)")
    List<GetEventsResponseDto> getEventsByCategory(String category);

    @Query("SELECT new wamddu.backend.event.dto.response.WhatsHotResponseDto(" +
            "DENSE_RANK () OVER (ORDER BY COALESCE(SUM(T.sold_ticket), 0) DESC), " +
            "E.id, E.name, E.startDate, E.endDate, E.location, E.bannerImageUrl, E.mainImageUrl, E.category ) " +
            "FROM Event E LEFT JOIN Ticket T ON T.event = E " +
            "WHERE (:category IS NULL OR :category = E.category) " +
            "GROUP BY E " +
            "ORDER BY COALESCE(SUM(T.sold_ticket), 0) DESC")
    List<WhatsHotResponseDto> whatshot(String category, Pageable pageable);

    @Query("SELECT new wamddu.backend.event.dto.response.WeeklyRankingResponseDto(" +
            "DENSE_RANK () OVER (ORDER BY COALESCE(SUM(T.sold_ticket), 0) DESC), " +
            "E.id, E.name, E.startDate, E.endDate, E.location, E.mainImageUrl, E.category ) " +
            "FROM Event E LEFT JOIN Ticket T ON T.event = E " +
            "WHERE (:category IS NULL OR :category = E.category) " +
            "GROUP BY E " +
            "ORDER BY COALESCE(SUM(T.sold_ticket), 0) DESC")
    List<WeeklyRankingResponseDto> weeklyRanking(String category, Pageable pageable);
}
