package wamddu.backend.order.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import wamddu.backend.event.domain.Event;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.order.dto.request.CreateOrderRequest;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.user.domain.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_CONCURRENCY", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3308/free_ticket_test?serverTimezone=Asia/Seoul",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.datasource.username=root",
        "spring.datasource.password=concurrency-test",
        "spring.datasource.hikari.maximum-pool-size=110",
        "spring.datasource.hikari.connection-timeout=60000",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class LastTicketConcurrencyTest {
    private static final int BUYERS = 100;

    @Autowired OrderService orderService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;

    @Test
    @Timeout(180)
    void oneRemainingTicketVsOneHundredBuyers() throws Exception {
        var transaction = new TransactionTemplate(transactionManager);
        Fixture fixture = transaction.execute(status -> {
            Event event = new Event();
            event.setName("Concurrency test");
            event.setStartDate(LocalDate.now().plusDays(1));
            event.setEndDate(LocalDate.now().plusDays(2));
            event.setLocation("Test venue");
            event.setBannerImageUrl("banner");
            event.setMainImageUrl("image");
            entityManager.persist(event);

            Ticket ticket = new Ticket();
            ticket.setEvent(event);
            ticket.setType("Last ticket");
            ticket.setPrice(10000);
            ticket.setTotal_ticket(1);
            ticket.setSold_ticket(0);
            ticket.setBookingEndtime(LocalDateTime.now().plusDays(1));
            entityManager.persist(ticket);

            List<Long> userIds = new ArrayList<>();
            for (int i = 0; i < BUYERS; i++) {
                User user = User.builder().username("Buyer " + i).password("test-only")
                        .email("concurrency-" + i + "@example.test")
                        .customerKey("concurrency-" + i).build();
                entityManager.persist(user);
                userIds.add(user.getId());
            }
            return new Fixture(ticket.getId(), userIds);
        });

        CreateOrderRequest request = new CreateOrderRequest();
        request.setTicketId(fixture.ticketId());
        request.setQuantity(1);
        var ready = new CountDownLatch(BUYERS);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(BUYERS);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (Long userId : fixture.userIds()) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Start signal timed out");
                    }
                    try {
                        orderService.createOrder(request, userId);
                        return "CREATED";
                    } catch (ApiException exception) {
                        return exception.getCode();
                    }
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(120, TimeUnit.SECONDS));
            }
            var counts = results.stream().collect(Collectors.groupingBy(
                    result -> result, Collectors.counting()));
            long savedOrders = transaction.execute(status -> entityManager.createQuery(
                            "select count(o) from Order o where o.ticket_id = :ticketId", Long.class)
                    .setParameter("ticketId", fixture.ticketId()).getSingleResult());
            int soldTickets = transaction.execute(status ->
                    entityManager.find(Ticket.class, fixture.ticketId()).getSold_ticket());
            String isolation = transaction.execute(status ->
                    (String) entityManager.createNativeQuery("select @@transaction_isolation").getSingleResult());
            System.out.printf("100 buyers, 1 ticket: isolation=%s, results=%s, savedOrders=%d, soldTickets=%d%n",
                    isolation, counts, savedOrders, soldTickets);

            assertThat(counts.getOrDefault("CREATED", 0L)).isEqualTo(savedOrders);
            assertThat(soldTickets).isZero();
            assertThat(counts.getOrDefault("CREATED", 0L)).isEqualTo(1L);
            assertThat(counts.getOrDefault("TICKET_SOLD_OUT", 0L)).isEqualTo(99L);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private record Fixture(Long ticketId, List<Long> userIds) {}
}
