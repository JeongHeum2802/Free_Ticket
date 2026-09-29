package wamddu.backend.admin;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.global.security.JwtProvider;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.domain.PaymentReconciliationIssue;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.payment.repository.PaymentReconciliationIssueRepository;
import wamddu.backend.user.domain.Role;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.domain.UserStatus;
import wamddu.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin_reconciliation;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class AdminPaymentReconciliationTest {
    private static final String PATH = "/api/admin/payment-reconciliation/issues";
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired PaymentReconciliationIssueRepository issues;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TossPaymentsClient toss;
    User admin;
    String adminToken;

    @BeforeEach
    void setUp() {
        admin = users.save(User.builder().username("Admin").password("test-only")
                .email(UUID.randomUUID() + "@example.test").role(Role.ADMIN).build());
        adminToken = "Bearer " + jwt.generateJwtToken(admin.getId(), "ADMIN");
    }

    private PaymentReconciliationIssue issue(String orderId, String type, LocalDateTime detectedAt) {
        return issues.save(PaymentReconciliationIssue.builder().merchantId("mid")
                .transactionKey(UUID.randomUUID().toString()).orderId(orderId).pgPaymentKey("private-pg-key")
                .issueType(type).pgStatus("CANCELED").dbOrderStatus("PAID").dbPaymentStatus("DONE")
                .transactionAtUtc(detectedAt.minusMinutes(10)).detectedAtUtc(detectedAt).build());
    }

    @Test
    void authenticatedAdminCanReadPaymentTablesButCannotChangeThemThroughGenericApi() throws Exception {
        var order = orders.saveAndFlush(Order.builder().orderId("ORD-READ-ONLY").user(admin)
                .quantity(1).unitPrice(5000).totalAmount(5000L).status(OrderStatus.PAID)
                .paymentKey("private-pg-key").idempotencyKey(UUID.randomUUID().toString()).build());
        payments.saveAndFlush(Payment.createPayment(order, "private-pg-key", 5000L, "카드", "DONE", null, null));
        for (String table : java.util.List.of("orders", "payments")) {
            String path = "/api/admin/tables/" + table;
            mvc.perform(get(path).header("Authorization", adminToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalRows").value(1))
                    .andExpect(jsonPath("$.data.createFields").isEmpty());
            for (String body : java.util.List.of(
                    "{\"changes\":[{\"id\":\"1\",\"originalValues\":{\"status\":\"PAID\"},\"values\":{\"status\":\"CANCELED\"}}]}",
                    "{\"creations\":[{\"status\":\"PAID\"}]}",
                    "{\"deletions\":[{\"id\":\"1\",\"originalValues\":{\"id\":\"1\"}}]}")) {
                mvc.perform(patch(path).header("Authorization", adminToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("ADMIN_TABLE_READ_ONLY"));
            }
        }
        assertThat(jdbc.queryForList("SELECT status FROM orders", String.class)).containsExactly("PAID");
        assertThat(jdbc.queryForList("SELECT status FROM payments", String.class)).containsExactly("DONE");
        verifyNoInteractions(toss);
    }

    @Test
    void filtersByOrderTypeAndUtcRangeAndReturnsStablePages() throws Exception {
        var time = LocalDateTime.of(2026, 9, 29, 0, 30);
        issue("ORD-A", "PAID_PG_CANCELED", time.minusDays(1));
        var first = issue("ORD-A", "PAID_PG_CANCELED", time);
        var second = issue("ORD-A", "PAID_PG_CANCELED", time);
        issue("ORD-A", "PAID_PG_PARTIAL_CANCELED", time);
        issue("ORD-B", "PAID_PG_CANCELED", time);
        issue("ORD-A", "PAID_PG_CANCELED", time.plusDays(1));

        for (int page = 0; page < 2; page++) {
            mvc.perform(get(PATH).header("Authorization", adminToken)
                    .param("orderId", " ORD-A ").param("issueType", "PAID_PG_CANCELED")
                    .param("from", "2026-09-29T09:30:00+09:00")
                    .param("to", "2026-09-30T09:30:00+09:00")
                    .param("page", Integer.toString(page)).param("size", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                    .andExpect(jsonPath("$.data.totalPages").value(2))
                    .andExpect(jsonPath("$.data.items[0].id").value(page == 0 ? second.getId() : first.getId()))
                    .andExpect(jsonPath("$.data.items[0].detectedAtUtc").value("2026-09-29T00:30:00Z"))
                    .andExpect(jsonPath("$.data.items[0].pgPaymentKey").doesNotExist());
        }
        verifyNoInteractions(toss);
    }

    @Test
    void defaultPageSizeAndLatestFirstOrderAreApplied() throws Exception {
        issue("ORD-OLD", "PAID_PG_CANCELED", LocalDateTime.of(2026, 9, 28, 0, 0));
        issue("ORD-NEW", "PAID_PG_CANCELED", LocalDateTime.of(2026, 9, 29, 0, 0));
        mvc.perform(get(PATH).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items[0].orderId").value("ORD-NEW"));
    }

    @Test
    void detailSeparatesImmutableDetectionSnapshotFromCurrentLocalState() throws Exception {
        var record = issue("ORD-CHANGED", "PAID_PG_CANCELED", LocalDateTime.of(2026, 9, 29, 0, 30));
        var order = orders.save(Order.builder().orderId("ORD-CHANGED").user(admin)
                .quantity(2).unitPrice(5000).totalAmount(10000L).status(OrderStatus.CANCELED)
                .paymentKey("private-pg-key").idempotencyKey(UUID.randomUUID().toString()).build());
        payments.save(Payment.createPayment(order, "private-pg-key", 10000L, "카드", "CANCELED", null, null));

        mvc.perform(get(PATH + "/" + record.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.issue.dbOrderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.issue.dbPaymentStatus").value("DONE"))
                .andExpect(jsonPath("$.data.currentOrder.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.currentOrder.quantity").value(2))
                .andExpect(jsonPath("$.data.currentOrder.totalAmount").value(10000))
                .andExpect(jsonPath("$.data.currentPayment.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.checkedAtUtc").isString())
                .andExpect(jsonPath("$.data.issue.pgPaymentKey").doesNotExist());
        assertThat(issues.findById(record.getId()).orElseThrow().getDbOrderStatus()).isEqualTo("PAID");
        assertThat(orders.findByOrderId("ORD-CHANGED").orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED);
        verifyNoInteractions(toss);
    }

    @Test
    void missingLocalOrderIsRepresentedWithoutFailingDetail() throws Exception {
        var record = issue("ORD-MISSING", "UNMATCHED_PG_CANCELLATION", LocalDateTime.of(2026, 9, 29, 0, 0));
        mvc.perform(get(PATH + "/" + record.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.issue.orderId").value("ORD-MISSING"))
                .andExpect(jsonPath("$.data.currentOrder").doesNotExist())
                .andExpect(jsonPath("$.data.currentPayment").doesNotExist());
    }

    @Test
    void missingCurrentOrderStatusAndPaymentDoNotHideSnapshotOrAmounts() throws Exception {
        var record = issue("ORD-NO-STATUS", "PAID_PG_CANCELED", LocalDateTime.of(2026, 9, 29, 0, 0));
        orders.saveAndFlush(Order.builder().orderId("ORD-NO-STATUS").user(admin)
                .quantity(1).unitPrice(5000).totalAmount(5000L).status(null)
                .idempotencyKey(UUID.randomUUID().toString()).build());
        mvc.perform(get(PATH + "/" + record.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.issue.dbOrderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.currentOrder.status").doesNotExist())
                .andExpect(jsonPath("$.data.currentOrder.quantity").value(1))
                .andExpect(jsonPath("$.data.currentOrder.totalAmount").value(5000))
                .andExpect(jsonPath("$.data.currentPayment").doesNotExist());
    }

    @Test
    void missingIssueIsNotFoundAndInvalidFiltersAreRejected() throws Exception {
        mvc.perform(get(PATH + "/99999999").header("Authorization", adminToken))
                .andExpect(status().isNotFound());
        for (var param : java.util.Map.of("page", "-1", "size", "101", "issueType", "UNKNOWN",
                "orderId", "A".repeat(65), "from", "not-a-date").entrySet()) {
            mvc.perform(get(PATH).header("Authorization", adminToken)
                    .param(param.getKey(), param.getValue())).andExpect(status().isBadRequest());
        }
        mvc.perform(get(PATH).header("Authorization", adminToken)
                .param("from", "2026-09-30T00:00:00Z").param("to", "2026-09-29T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyCurrentActiveAdminCanReadListAndDetail() throws Exception {
        var record = issue("ORD-A", "PAID_PG_CANCELED", LocalDateTime.of(2026, 9, 29, 0, 0));
        for (String path : java.util.List.of(PATH, PATH + "/" + record.getId())) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", "Bearer " + jwt.generateJwtToken(admin.getId(), "USER")))
                    .andExpect(status().isForbidden());
            admin.setRole(Role.USER);
            users.saveAndFlush(admin);
            mvc.perform(get(path).header("Authorization", adminToken))
                    .andExpect(status().isForbidden());
            admin.setRole(Role.ADMIN);
            admin.setStatus(UserStatus.DELETED);
            users.saveAndFlush(admin);
            mvc.perform(get(path).header("Authorization", adminToken))
                    .andExpect(status().isForbidden());
            admin.setStatus(UserStatus.ACTIVE);
            users.saveAndFlush(admin);
        }
    }
}
