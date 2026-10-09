package wamddu.backend.ticket.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import wamddu.backend.event.domain.Event;
import wamddu.backend.eventDirector.domain.EventDirector;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.dto.request.TicketPriceSettingsRequest;
import wamddu.backend.ticket.repository.TicketPriceHistoryRepository;
import wamddu.backend.ticket.repository.TicketRepository;
import wamddu.backend.user.domain.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ticket_pricing_test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "ticket.pricing.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TicketPricingIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 6, 1, 12, 0);

    @Autowired TicketPricingService pricingService;
    @Autowired OrderRepository orderRepository;
    @Autowired TicketPriceHistoryRepository historyRepository;
    @Autowired TicketRepository ticketRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;

    @Test
    void paidQuantityUsesUnitsAndIncludesOnlyTheRequestedTicketAndWindow() {
        var tx = new TransactionTemplate(transactionManager);
        Fixture fixture = tx.execute(status -> {
            Fixture created = createFixture(false);
            Ticket other = ticket(entityManager.find(Event.class, created.eventId()));
            entityManager.persist(other);
            User buyer = entityManager.find(User.class, created.directorId());
            LocalDateTime from = NOW.minusHours(2);
            order(buyer, created.ticketId(), created.eventId(), OrderStatus.PAID, 3, from);
            order(buyer, created.ticketId(), created.eventId(), OrderStatus.PAID, 4, from.plusMinutes(30));
            order(buyer, created.ticketId(), created.eventId(), OrderStatus.PAID, 20, from.minusSeconds(1));
            order(buyer, created.ticketId(), created.eventId(), OrderStatus.PAID, 20, NOW);
            order(buyer, created.ticketId(), created.eventId(), OrderStatus.PENDING, 20, from.plusMinutes(30));
            order(buyer, other.getId(), created.eventId(), OrderStatus.PAID, 20, from.plusMinutes(30));
            order(buyer, created.ticketId(), created.eventId(), OrderStatus.PAID, 20, null);
            return created;
        });

        assertThat(orderRepository.sumPaidQuantity(fixture.ticketId(), NOW.minusHours(2), NOW))
                .isEqualTo(7L);
        assertThat(orderRepository.sumPaidQuantity(fixture.ticketId(), NOW.plusHours(1), NOW.plusHours(2)))
                .isZero();
    }

    @Test
    void rollbackRevertsPriceHistoryAndEvaluationTogetherAndPreservesPaidOrderAmounts() {
        var tx = new TransactionTemplate(transactionManager);
        Fixture fixture = tx.execute(status -> {
            Fixture created = createFixture(true);
            Ticket ticket = entityManager.find(Ticket.class, created.ticketId());
            ticket.setSold_ticket(2);
            Order paid = order(entityManager.find(User.class, created.directorId()),
                    created.ticketId(), created.eventId(), OrderStatus.PAID, 2, NOW.minusHours(3));
            return new Fixture(created.directorId(), created.outsiderId(), created.eventId(),
                    created.ticketId(), paid.getId());
        });

        tx.executeWithoutResult(status -> {
            pricingService.adjustPrice(fixture.ticketId(), NOW);
            entityManager.flush();
            entityManager.clear();
            Ticket changed = entityManager.find(Ticket.class, fixture.ticketId());
            assertThat(changed.getPrice()).isEqualTo(9000);
            assertThat(changed.getLastPriceEvaluatedAt()).isEqualTo(NOW);
            assertThat(historyRepository.findAllByTicketIdOrderByChangedAtAscIdAsc(fixture.ticketId()))
                    .singleElement().satisfies(history -> {
                        assertThat(history.getPrice()).isEqualTo(9000);
                        assertThat(history.getChangedAt()).isEqualTo(NOW);
                    });
            assertPaidOrderUnchanged(fixture.paidOrderId());
            status.setRollbackOnly();
        });

        tx.executeWithoutResult(status -> {
            Ticket restored = entityManager.find(Ticket.class, fixture.ticketId());
            assertThat(restored.getPrice()).isEqualTo(10000);
            assertThat(restored.getLastPriceEvaluatedAt()).isNull();
            assertThat(historyRepository.findAllByTicketIdOrderByChangedAtAscIdAsc(fixture.ticketId())).isEmpty();
            assertPaidOrderUnchanged(fixture.paidOrderId());
        });
    }

    @Test
    void committedEvaluationPreventsAnotherDiscountUntilTheTwoHourBoundary() {
        var tx = new TransactionTemplate(transactionManager);
        Fixture fixture = tx.execute(status -> createFixture(true));

        pricingService.adjustPrice(fixture.ticketId(), NOW);
        pricingService.adjustPrice(fixture.ticketId(), NOW.plusMinutes(1));

        tx.executeWithoutResult(status -> {
            Ticket ticket = entityManager.find(Ticket.class, fixture.ticketId());
            assertThat(ticket.getPrice()).isEqualTo(9000);
            assertThat(ticket.getLastPriceEvaluatedAt()).isEqualTo(NOW);
            assertThat(historyRepository.findAllByTicketIdOrderByChangedAtAscIdAsc(fixture.ticketId()))
                    .singleElement().satisfies(history -> assertThat(history.getPrice()).isEqualTo(9000));
        });
        assertThat(ticketRepository.findPriceAdjustmentCandidates(NOW.plusMinutes(1),
                NOW.plusMinutes(1).minusHours(2))).doesNotContain(fixture.ticketId());
        assertThat(ticketRepository.findPriceAdjustmentCandidates(NOW.plusHours(2), NOW))
                .contains(fixture.ticketId());
    }

    @Test
    void nonDirectorCannotConfigureAnotherEventsTicket() {
        var tx = new TransactionTemplate(transactionManager);
        Fixture fixture = tx.execute(status -> createFixture(false));

        assertThatThrownBy(() -> pricingService.configure(fixture.outsiderId(), fixture.ticketId(),
                new TicketPriceSettingsRequest(10000, 5000, true, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("TICKET_NOT_FOUND"));

        tx.executeWithoutResult(status -> {
            Ticket ticket = entityManager.find(Ticket.class, fixture.ticketId());
            assertThat(ticket.getPrice()).isEqualTo(10000);
            assertThat(ticket.getInitialPrice()).isNull();
            assertThat(ticket.getMinPrice()).isNull();
            assertThat(ticket.isAutomaticPricingEnabled()).isFalse();
            assertThat(ticket.getSalesStartAt()).isNull();
            assertThat(historyRepository.findAllByTicketIdOrderByChangedAtAscIdAsc(fixture.ticketId())).isEmpty();
        });
    }

    private Fixture createFixture(boolean automaticPricing) {
        User director = user("director");
        User outsider = user("outsider");
        Event event = new Event();
        event.setName("Pricing integration");
        event.setStartDate(LocalDate.of(2030, 6, 2));
        event.setEndDate(LocalDate.of(2030, 6, 3));
        event.setLocation("Test venue");
        event.setBannerImageUrl("test-banner");
        event.setMainImageUrl("test-main");
        entityManager.persist(event);
        EventDirector managed = new EventDirector();
        managed.setUser(director);
        managed.setEvent(event);
        entityManager.persist(managed);
        Ticket ticket = ticket(event);
        if (automaticPricing) {
            ticket.setInitialPrice(10000);
            ticket.setMinPrice(5000);
            ticket.setAutomaticPricingEnabled(true);
            ticket.setSalesStartAt(NOW.minusHours(4));
        }
        entityManager.persist(ticket);
        return new Fixture(director.getId(), outsider.getId(), event.getId(), ticket.getId(), null);
    }

    private User user(String name) {
        String key = UUID.randomUUID().toString();
        User user = User.builder().username(name).password("test-only")
                .email(key + "@example.test").customerKey(key).build();
        entityManager.persist(user);
        return user;
    }

    private Ticket ticket(Event event) {
        Ticket ticket = new Ticket();
        ticket.setEvent(event);
        ticket.setType("General");
        ticket.setPrice(10000);
        ticket.setTotal_ticket(100);
        ticket.setSold_ticket(0);
        ticket.setBookingEndtime(NOW.plusHours(6));
        return ticket;
    }

    private Order order(User buyer, Long ticketId, Long eventId, OrderStatus status, int quantity,
                        LocalDateTime paidAt) {
        Order order = Order.createPendingOrder(UUID.randomUUID().toString(), buyer, ticketId, eventId,
                quantity, 10000, UUID.randomUUID().toString(), 10);
        order.setStatus(status);
        order.setPaidAt(paidAt);
        order.setExpiresAt(NOW.plusMinutes(10));
        entityManager.persist(order);
        return order;
    }

    private void assertPaidOrderUnchanged(Long orderId) {
        Order paid = entityManager.find(Order.class, orderId);
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.getUnitPrice()).isEqualTo(10000);
        assertThat(paid.getTotalAmount()).isEqualTo(20000L);
    }

    private record Fixture(Long directorId, Long outsiderId, Long eventId, Long ticketId, Long paidOrderId) {}
}
