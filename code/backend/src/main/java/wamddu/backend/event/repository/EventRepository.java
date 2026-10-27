package wamddu.backend.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.dto.response.EventSummaryResponse;
import wamddu.backend.event.dto.response.WhatsHotEventResponse;

import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.repository.query.Param;

@Repository
public interface EventRepository extends JpaRepository<Event,Long> {

    @Query("SELECT DISTINCT E.category FROM Event E")
    List<String> findAllCategories();

    @Query("SELECT new wamddu.backend.event.dto.response.EventSummaryResponse(" +
            "E.id, E.name, E.startDate, E.endDate, E.location, E.mainImageUrl, E.category, E.description ) " +
            "FROM Event E " +
            "WHERE (:category IS NULL OR :category = E.category) " +
            "AND (:title IS NULL OR E.name LIKE :title ESCAPE '!') " +
            "AND (:region IS NULL OR E.location LIKE :region ESCAPE '!') " +
            "ORDER BY E.id DESC")
    List<EventSummaryResponse> searchEvents(String category, String title, String region);

    // One matching ticket must satisfy all conditions. EXISTS keeps each event unique.
    // Active holds follow OrderRepository.sumActiveQuantity, including recovery states.
    @Query("""
            SELECT new wamddu.backend.event.dto.response.EventSummaryResponse(
                E.id, E.name, E.startDate, E.endDate, E.location, E.mainImageUrl, E.category, E.description)
            FROM Event E
            WHERE (:category IS NULL OR E.category = :category)
              AND (:title IS NULL OR E.name LIKE :title ESCAPE '!')
              AND (:region IS NULL OR E.location LIKE :region ESCAPE '!')
              AND EXISTS (
                SELECT T.id FROM Ticket T WHERE T.event = E
                  AND (:fromTime IS NULL OR T.start_time >= :fromTime)
                  AND (:toTime IS NULL OR T.start_time < :toTime)
                  AND (:maxPrice IS NULL OR (T.price >= 0 AND T.price <= :maxPrice))
                  AND (:availableOnly = false OR (
                    T.price >= 0 AND T.bookingEndtime > :now
                    AND T.total_ticket - T.sold_ticket > (
                      SELECT COALESCE(SUM(O.quantity), 0) FROM Order O
                      WHERE O.ticket_id = T.id
                        AND O.status IN (wamddu.backend.order.domain.OrderStatus.PENDING,
                          wamddu.backend.order.domain.OrderStatus.CONFIRMING,
                          wamddu.backend.order.domain.OrderStatus.CANCELING)
                        AND (O.status <> wamddu.backend.order.domain.OrderStatus.CANCELING
                          OR NOT EXISTS (SELECT P.id FROM Payment P WHERE P.order = O))
                        AND (O.expiresAt > :now OR O.status IN (
                          wamddu.backend.order.domain.OrderStatus.CONFIRMING,
                          wamddu.backend.order.domain.OrderStatus.CANCELING))
                    )
                  ))
              )
            ORDER BY E.id DESC
            """)
    List<EventSummaryResponse> searchEventsWithTickets(
            @Param("category") String category, @Param("title") String title, @Param("region") String region,
            @Param("fromTime") LocalDateTime fromTime, @Param("toTime") LocalDateTime toTime,
            @Param("maxPrice") Integer maxPrice, @Param("availableOnly") boolean availableOnly,
            @Param("now") LocalDateTime now);

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

}
