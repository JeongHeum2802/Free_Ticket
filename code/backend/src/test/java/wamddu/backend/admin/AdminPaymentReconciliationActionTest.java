package wamddu.backend.admin;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.global.security.JwtProvider;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.domain.PaymentReconciliationIssue;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.event.domain.Event;
import wamddu.backend.user.domain.Role;
import wamddu.backend.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin_actions;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureMockMvc
class AdminPaymentReconciliationActionTest {
    private static final String PATH = "/api/admin/payment-reconciliation/issues/";
    private final ObjectMapper json = new ObjectMapper();
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JwtProvider jwt;
    @MockitoBean TossPaymentsClient toss;
    TransactionTemplate tx;
    long issueId, otherIssueId, orderPk, ticketId, adminId;
    String token, orderId, key;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            var user = User.builder().username("Operator").password("test")
                    .email(UUID.randomUUID() + "@example.test").role(Role.ADMIN).build();
            em.persist(user);
            adminId = user.getId();
            var event = new Event();
            event.setName("Cancellation"); event.setLocation("Test");
            event.setStartDate(LocalDate.now()); event.setEndDate(LocalDate.now().plusDays(1));
            event.setBannerImageUrl("banner"); event.setMainImageUrl("main");
            em.persist(event);
            var ticket = new Ticket();
            ticket.setEvent(event); ticket.setType("TEST"); ticket.setPrice(5000);
            ticket.setTotal_ticket(10); ticket.setSold_ticket(5);
            em.persist(ticket); ticketId = ticket.getId();
            orderId = "ORD-" + UUID.randomUUID(); key = "pay-" + UUID.randomUUID();
            var order = Order.builder().orderId(orderId).user(user).ticket_id(ticketId).event_id(event.getId())
                    .quantity(2).unitPrice(5000).totalAmount(10000L).status(OrderStatus.PAID)
                    .paymentKey(key).idempotencyKey(UUID.randomUUID().toString()).build();
            em.persist(order); orderPk = order.getId();
            em.persist(Payment.createPayment(order, key, 10000L, "카드", "DONE", LocalDateTime.now(), null));
            issueId = newIssue(); otherIssueId = newIssue();
        });
        token = "Bearer " + jwt.generateJwtToken(adminId, "ADMIN");
        when(toss.getPayment(key)).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return pg("CANCELED", 0, 10000, "DONE", "mid", orderId);
        });
    }

    private long newIssue() {
        var issue = PaymentReconciliationIssue.builder().merchantId("mid").transactionKey(UUID.randomUUID().toString())
                .orderId(orderId).pgPaymentKey(key).issueType("PAID_PG_CANCELED").pgStatus("CANCELED")
                .dbOrderStatus("PAID").dbPaymentStatus("DONE")
                .transactionAtUtc(LocalDateTime.now()).detectedAtUtc(LocalDateTime.now()).build();
        em.persist(issue); return issue.getId();
    }

    private TossPaymentsClient.TossPaymentResponse pg(String status, long balance, long canceled,
                                                       String cancelStatus, String mid, String pgOrderId) {
        return json.readValue("""
                {"paymentKey":"%s","orderId":"%s","mId":"%s","currency":"KRW","status":"%s",
                 "totalAmount":10000,"balanceAmount":%d,"method":"카드",
                 "cancels":[{"cancelAmount":%d,"cancelStatus":"%s","canceledAt":"2026-09-29T10:00:00+09:00"}]}
                """.formatted(key, pgOrderId, mid, status, balance, canceled, cancelStatus),
                TossPaymentsClient.TossPaymentResponse.class);
    }

    private org.springframework.test.web.servlet.ResultActions action(long id, String action) throws Exception {
        return mvc.perform(post(PATH + id + "/" + action).header("Authorization", token)
                .contentType("application/json").content("{\"reason\":\"고객 취소 확인\"}"));
    }

    private void assertLocal(String orderStatus, String paymentStatus, int sold) {
        tx.executeWithoutResult(s -> {
            assertThat(em.find(Order.class, orderPk).getStatus().name()).isEqualTo(orderStatus);
            assertThat(em.createQuery("select p.status from Payment p where p.order.id = :id", String.class)
                    .setParameter("id", orderPk).getSingleResult()).isEqualTo(paymentStatus);
            assertThat(em.find(Ticket.class, ticketId).getSold_ticket()).isEqualTo(sold);
        });
    }

    @Test
    void recheckRecordsVerifiedPgEvidenceWithoutChangingLocalState() throws Exception {
        action(issueId, "recheck").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestCheck.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.latestCheck.balanceAmount").value(0))
                .andExpect(jsonPath("$.data.latestCheck.cancels[0].amount").value(10000))
                .andExpect(jsonPath("$.data.latestCheck.canApplyFullCancellation").value(true))
                .andExpect(jsonPath("$.data.history[0].actorId").value(adminId))
                .andExpect(jsonPath("$.data.history[0].action").value("RECHECK"))
                .andExpect(jsonPath("$.data.history[0].pg.paymentKey").doesNotExist());
        assertLocal("PAID", "DONE", 5);
    }

    @Test
    void freshFullCancellationAtomicallyResolvesAndReturnsStockOnceAcrossIssues() throws Exception {
        action(issueId, "apply-full-cancellation").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issue.resolvedAtUtc").isString())
                .andExpect(jsonPath("$.data.history[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.history[0].soldBefore").value(5))
                .andExpect(jsonPath("$.data.history[0].soldAfter").value(3));
        action(issueId, "apply-full-cancellation").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history[0].result").value("NO_CHANGE"));
        action(otherIssueId, "apply-full-cancellation").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issue.resolvedAtUtc").isString());
        assertLocal("CANCELED", "CANCELED", 3);
        verify(toss, times(3)).getPayment(key);
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void concurrentOperatorsReturnStockOnlyOnce() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(
                    () -> action(issueId, "apply-full-cancellation").andReturn().getResponse().getStatus(),
                    () -> action(otherIssueId, "apply-full-cancellation").andReturn().getResponse().getStatus()));
            for (var result : results) assertThat(result.get()).isEqualTo(200);
        }
        assertLocal("CANCELED", "CANCELED", 3);
    }

    @Test
    void staleRecheckCannotAuthorizeAChangedPgState() throws Exception {
        action(issueId, "recheck").andExpect(status().isOk());
        when(toss.getPayment(key)).thenReturn(pg("DONE", 10000, 0, "DONE", "mid", orderId));
        action(issueId, "apply-full-cancellation").andExpect(status().isConflict());
        assertLocal("PAID", "DONE", 5);
        mvc.perform(get(PATH + issueId).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issue.resolvedAtUtc").doesNotExist())
                .andExpect(jsonPath("$.data.latestCheck.identityMatches").value(true))
                .andExpect(jsonPath("$.data.history[0].beforeOrderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.history[0].afterOrderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.history[0].result").value("FAILED"));
    }

    @Test
    void partialZeroBalanceIncompleteCancelAndIdentityMismatchAreBlocked() throws Exception {
        var responses = List.of(pg("PARTIAL_CANCELED", 0, 10000, "DONE", "mid", orderId),
                pg("CANCELED", 1, 10000, "DONE", "mid", orderId),
                pg("CANCELED", 0, 9000, "DONE", "mid", orderId),
                pg("CANCELED", 0, 10000, "PENDING", "mid", orderId),
                pg("CANCELED", 0, 10000, "DONE", "wrong-mid", orderId),
                pg("CANCELED", 0, 10000, "DONE", "mid", "ORD-OTHER"));
        for (var response : responses) {
            when(toss.getPayment(key)).thenReturn(response);
            action(issueId, "apply-full-cancellation").andExpect(status().isConflict());
            assertLocal("PAID", "DONE", 5);
        }
    }

    @Test
    void lookupFailureLeavesDurableFailureHistory() throws Exception {
        when(toss.getPayment(key)).thenThrow(new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                "TOSS_LOOKUP_FAILED", "조회 실패"));
        action(issueId, "recheck").andExpect(status().isBadGateway());
        mvc.perform(get(PATH + issueId).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history[0].errorCode").value("TOSS_LOOKUP_FAILED"))
                .andExpect(jsonPath("$.data.history[0].result").value("FAILED"));
        assertLocal("PAID", "DONE", 5);
    }

    @Test
    void databaseFailureRollsBackStateStockResolutionAndSuccessHistory() throws Exception {
        tx.executeWithoutResult(s -> em.createNativeQuery("ALTER TABLE tickets ADD CONSTRAINT test_stock_floor CHECK (id <> "
                + ticketId + " OR sold_ticket >= 5)").executeUpdate());
        try {
            action(issueId, "apply-full-cancellation").andExpect(status().isServiceUnavailable());
            assertLocal("PAID", "DONE", 5);
            mvc.perform(get(PATH + issueId).header("Authorization", token)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.issue.resolvedAtUtc").doesNotExist())
                    .andExpect(jsonPath("$.data.history.length()").value(1))
                    .andExpect(jsonPath("$.data.history[0].result").value("FAILED"));
        } finally {
            tx.executeWithoutResult(s -> em.createNativeQuery("ALTER TABLE tickets DROP CONSTRAINT test_stock_floor").executeUpdate());
        }
        action(issueId, "apply-full-cancellation").andExpect(status().isOk());
        assertLocal("CANCELED", "CANCELED", 3);
    }

    @Test
    void localAmountMismatchOrUnprovenCanceledStateCannotReturnStock() throws Exception {
        tx.executeWithoutResult(s -> em.createQuery("update Payment p set p.amount=9000 where p.order.id=:id").setParameter("id", orderPk).executeUpdate());
        action(issueId, "apply-full-cancellation").andExpect(status().isConflict());
        tx.executeWithoutResult(s -> {
            em.createQuery("update Payment p set p.amount=10000, p.status='CANCELED' where p.order.id=:id").setParameter("id", orderPk).executeUpdate();
            em.find(Order.class, orderPk).setStatus(OrderStatus.CANCELED);
        });
        action(issueId, "apply-full-cancellation").andExpect(status().isConflict());
        assertLocal("CANCELED", "CANCELED", 5);
    }

    @Test
    void manuallyResetPaidStateCannotReturnStockAgain() throws Exception {
        action(issueId, "apply-full-cancellation").andExpect(status().isOk());
        tx.executeWithoutResult(s -> {
            em.find(Order.class, orderPk).setStatus(OrderStatus.PAID);
            em.createQuery("update Payment p set p.status='DONE' where p.order.id=:id").setParameter("id", orderPk).executeUpdate();
        });
        action(otherIssueId, "apply-full-cancellation").andExpect(status().isConflict());
        assertLocal("PAID", "DONE", 3);
    }

    @Test
    void missingPaymentAndInsufficientStockStayUnresolved() throws Exception {
        tx.executeWithoutResult(s -> em.find(Ticket.class, ticketId).setSold_ticket(1));
        action(issueId, "apply-full-cancellation").andExpect(status().isConflict());
        assertLocal("PAID", "DONE", 1);
        tx.executeWithoutResult(s -> em.createQuery("delete from Payment p where p.order.id=:id").setParameter("id", orderPk).executeUpdate());
        action(issueId, "recheck").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestCheck.canApplyFullCancellation").value(false));
        action(issueId, "apply-full-cancellation").andExpect(status().isConflict());
    }

    @Test
    void malformedPgCancellationTimeIsRejectedWithoutHidingFailureHistory() throws Exception {
        var malformed = json.readValue(json.writeValueAsString(pg("CANCELED", 0, 10000, "DONE", "mid", orderId))
                .replace("2026-09-29T10:00:00+09:00", "invalid-date"), TossPaymentsClient.TossPaymentResponse.class);
        when(toss.getPayment(key)).thenReturn(malformed);
        action(issueId, "recheck").andExpect(status().isBadGateway());
        mvc.perform(get(PATH + issueId).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history[0].result").value("FAILED"));
        assertLocal("PAID", "DONE", 5);
    }

    @Test
    void noteDoesNotResolveAndMutationsRequireCurrentAdminAndReason() throws Exception {
        action(issueId, "notes").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history[0].reason").value("고객 취소 확인"))
                .andExpect(jsonPath("$.data.issue.resolvedAtUtc").doesNotExist());
        for (String operation : List.of("recheck", "apply-full-cancellation", "notes")) {
            mvc.perform(post(PATH + issueId + "/" + operation).contentType("application/json").content("{\"reason\":\"test\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(PATH + issueId + "/" + operation).header("Authorization", token)
                    .contentType("application/json").content("{\"reason\":\"  \"}"))
                    .andExpect(status().isBadRequest());
        }
        tx.executeWithoutResult(s -> em.find(User.class, adminId).setRole(Role.USER));
        action(issueId, "apply-full-cancellation").andExpect(status().isForbidden());
        verifyNoInteractions(toss);
    }
}
