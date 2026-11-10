package wamddu.backend.order.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import wamddu.backend.event.domain.Event;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.dto.request.CreateOrderRequest;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.payment.dto.request.ConfirmPaymentRequest;
import wamddu.backend.payment.service.PaymentService;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.user.domain.Role;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.domain.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/** One buyer must not be able to keep tickets away from everyone else. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:inventory_hold_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "payment.recovery.enabled=false", "payment.recovery.max-attempts=20", "payment.recovery.retry-delay-ms=0"
})
@DirtiesContext
class InventoryHoldIntegrationTest {
    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired OrderRepository orders;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean TossPaymentsClient toss;

    TransactionTemplate tx;
    Long holder;
    Long otherBuyer;

    @BeforeEach
    void setUp() {
        reset(toss);
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            for (String entity : List.of("Payment", "Order", "Ticket", "Event", "User")) {
                em.createQuery("delete from " + entity).executeUpdate();
            }
        });
        holder = user("holder@example.test");
        otherBuyer = user("other@example.test");
    }

    Long user(String email) {
        return tx.execute(s -> {
            User user = User.builder().username("Buyer").password("test-only").email(email)
                    .customerKey("c_" + email).role(Role.USER).status(UserStatus.ACTIVE).build();
            em.persist(user);
            return user.getId();
        });
    }

    Long ticket(int total) {
        return tx.execute(s -> {
            Event event = new Event();
            event.setName("Hold test");
            event.setStartDate(LocalDate.now().plusDays(1));
            event.setEndDate(LocalDate.now().plusDays(2));
            event.setLocation("venue");
            event.setBannerImageUrl("banner");
            event.setMainImageUrl("main");
            em.persist(event);
            Ticket ticket = new Ticket();
            ticket.setEvent(event);
            ticket.setType("ticket");
            ticket.setPrice(10000);
            ticket.setTotal_ticket(total);
            ticket.setSold_ticket(0);
            ticket.setBookingEndtime(LocalDateTime.now().plusDays(1));
            em.persist(ticket);
            return ticket.getId();
        });
    }

    String order(Long ticketId, Long userId, int quantity) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setTicketId(ticketId);
        request.setQuantity(quantity);
        return orderService.createOrder(request, userId).orderId();
    }

    Order find(String orderId) {
        return tx.execute(s -> orders.findByOrderId(orderId).orElseThrow());
    }

    void expire(String orderId, LocalDateTime expiresAt) {
        tx.executeWithoutResult(s -> orders.findByOrderId(orderId).orElseThrow().setExpiresAt(expiresAt));
    }

    /** Confirms with a key Toss does not know, leaving the order CONFIRMING. */
    String confirmWithUnknownKey(Long ticketId) {
        String orderId = order(ticketId, holder, 1);
        when(toss.confirm(any(), any(), any(), any())).thenThrow(
                new ApiException(HttpStatus.BAD_GATEWAY, "TOSS_CONFIRM_FAILED", "unknown payment key"));
        assertThatThrownBy(() -> paymentService.confirm(holder, new ConfirmPaymentRequest("bogus_key", orderId, 10000L)))
                .isInstanceOf(ApiException.class);
        assertThat(find(orderId).getStatus()).isEqualTo(OrderStatus.CONFIRMING);
        return orderId;
    }

    void runRecovery(int times) {
        for (int i = 0; i < times; i++) paymentService.recoverPendingPayments();
    }

    void lookupNotFound() {
        when(toss.getPayment(any())).thenThrow(new ApiException(
                HttpStatus.BAD_GATEWAY, TossPaymentsClient.PAYMENT_NOT_FOUND, "not found"));
    }

    @Test
    void unknownPaymentKeyIsFailedAfterGraceAndReleasesTheTicket() {
        Long ticketId = ticket(1);
        String orderId = confirmWithUnknownKey(ticketId);
        expire(orderId, LocalDateTime.now().minusHours(1));
        lookupNotFound();

        runRecovery(3);

        assertThat(find(orderId).getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order(ticketId, otherBuyer, 1)).isNotBlank();
    }

    @Test
    void unknownPaymentKeyKeepsHoldingWithinGracePeriod() {
        Long ticketId = ticket(1);
        String orderId = confirmWithUnknownKey(ticketId);
        expire(orderId, LocalDateTime.now().minusMinutes(5));
        lookupNotFound();

        runRecovery(5);

        assertThat(find(orderId).getStatus()).isEqualTo(OrderStatus.CONFIRMING);
    }

    @Test
    void unknownPaymentKeyNeedsSeveralLookups() {
        Long ticketId = ticket(1);
        String orderId = confirmWithUnknownKey(ticketId);
        expire(orderId, LocalDateTime.now().minusHours(1));
        lookupNotFound();

        runRecovery(2);

        assertThat(find(orderId).getStatus()).isEqualTo(OrderStatus.CONFIRMING);
    }

    @Test
    void otherLookupFailuresKeepHoldingBecauseThePaymentMayExist() {
        Long ticketId = ticket(1);
        String orderId = confirmWithUnknownKey(ticketId);
        expire(orderId, LocalDateTime.now().minusHours(1));
        when(toss.getPayment(any())).thenThrow(
                new ApiException(HttpStatus.BAD_GATEWAY, "TOSS_LOOKUP_FAILED", "server error"));

        runRecovery(5);

        assertThat(find(orderId).getStatus()).isEqualTo(OrderStatus.CONFIRMING);
        assertThatThrownBy(() -> order(ticketId, otherBuyer, 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("TICKET_SOLD_OUT");
    }

    @Test
    void oneUserCannotHoldMoreThanTenOfATicket() {
        Long ticketId = ticket(100);
        order(ticketId, holder, 6);
        order(ticketId, holder, 4);

        assertThatThrownBy(() -> order(ticketId, holder, 1))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("ORDER_LIMIT_EXCEEDED");
        assertThat(order(ticketId, otherBuyer, 10)).isNotBlank();
    }

    @Test
    void expiredPendingOrdersDoNotCountTowardTheLimit() {
        Long ticketId = ticket(100);
        String first = order(ticketId, holder, 10);
        expire(first, LocalDateTime.now().minusMinutes(1));

        assertThat(order(ticketId, holder, 10)).isNotBlank();
    }

    @Test
    void holdLimitIsPerTicket() {
        Long firstTicket = ticket(100);
        Long secondTicket = ticket(100);
        order(firstTicket, holder, 10);

        assertThat(order(secondTicket, holder, 10)).isNotBlank();
    }
}
