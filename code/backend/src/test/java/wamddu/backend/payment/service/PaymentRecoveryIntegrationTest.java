package wamddu.backend.payment.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import wamddu.backend.event.domain.Event;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.dto.request.CreateOrderRequest;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.order.service.OrderService;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.dto.request.ConfirmPaymentRequest;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.user.domain.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payment_recovery_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentRecoveryIntegrationTest {
    @Autowired PaymentService service;
    @Autowired OrderService orderService;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean TossPaymentsClient toss;
    @MockitoSpyBean PaymentRepository payments;
    @MockitoSpyBean OrderRepository orders;
    TransactionTemplate tx;
    Long userId;
    Long ticketId;
    ConfirmPaymentRequest request;
    String approvalKey;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            String unique = UUID.randomUUID().toString();
            User user = User.builder().username("Recovery buyer").password("test-only")
                    .email(unique + "@example.test").customerKey(unique).build();
            em.persist(user);
            userId = user.getId();
            Event event = new Event();
            event.setName("Recovery test");
            event.setStartDate(LocalDate.now().plusDays(1));
            event.setEndDate(LocalDate.now().plusDays(2));
            event.setLocation("Test venue");
            event.setBannerImageUrl("banner");
            event.setMainImageUrl("main");
            em.persist(event);
            Ticket ticket = new Ticket();
            ticket.setEvent(event);
            ticket.setType("Recovery ticket");
            ticket.setPrice(10000);
            ticket.setTotal_ticket(1);
            ticket.setSold_ticket(0);
            ticket.setBookingEndtime(LocalDateTime.now().plusDays(1));
            em.persist(ticket);
            ticketId = ticket.getId();
        });
        var create = new CreateOrderRequest();
        create.setTicketId(ticketId);
        create.setQuantity(1);
        String orderId = orderService.createOrder(create, userId).orderId();
        request = new ConfirmPaymentRequest("payment_" + UUID.randomUUID(), orderId, 10000L);
        approvalKey = tx.execute(s -> order().getIdempotencyKey());
        when(toss.confirm(request.paymentKey(), orderId, 10000L, approvalKey)).thenReturn(response("DONE"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void transientBodyFailureRetriesWithoutCancel(boolean apiException) {
        RuntimeException failure = apiException
                ? new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INJECTED", "save failed")
                : new IllegalStateException("save failed");
        var first = new AtomicBoolean(true);
        doAnswer(i -> {
            if (first.getAndSet(false)) throw failure;
            return mockingDetails(i.getMock()).getMockCreationSettings().getDefaultAnswer().answer(i);
        }).when(payments).save(any(Payment.class));

        assertThat(service.confirm(userId, request).status()).isEqualTo("DONE");
        assertDb("PAID", 1, 1);
        verify(payments, times(2)).save(any(Payment.class));
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void approvalResponseLossLeavesDurableRecoveryInformation() {
        when(toss.confirm(anyString(), anyString(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("approval response lost"));
        assertThatThrownBy(() -> service.confirm(userId, request)).isInstanceOf(RuntimeException.class);
        tx.executeWithoutResult(s -> {
            assertThat(order().getStatus()).isEqualTo(OrderStatus.CONFIRMING);
            assertThat(order()).extracting("paymentKey").isEqualTo(request.paymentKey());
            assertThat(order()).extracting("nextRecoveryAt").isNotNull();
        });
    }

    @Test
    void persistentSaveFailureStopsAfterOneRetryWithoutCancel() {
        doThrow(new IllegalStateException("DB unavailable")).when(payments).save(any(Payment.class));
        assertError("PAYMENT_RECOVERY_PENDING");
        assertDb("CONFIRMING", 0, 0);
        verify(payments, times(2)).save(any(Payment.class));
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void recoveryReadFailureDoesNotCancel() {
        doThrow(new IllegalStateException("save failed")).when(payments).save(any(Payment.class));
        doThrow(new IllegalStateException("read failed")).when(payments).findByOrderOrderId(request.orderId());
        assertError("PAYMENT_RECOVERY_PENDING");
        assertDb("CONFIRMING", 0, 0);
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"key", "order", "amount", "status"})
    void mismatchedApprovalDoesNotCompleteOrCancel(String field) {
        when(toss.confirm(anyString(), anyString(), anyLong(), anyString())).thenReturn(
                new TossPaymentsClient.TossPaymentResponse(field.equals("key") ? "wrong" : request.paymentKey(),
                        field.equals("order") ? "wrong" : request.orderId(),
                        field.equals("status") ? "CANCELED" : "DONE", "카드",
                        field.equals("amount") ? 1L : 10000L, null));
        assertError("PAYMENT_RECOVERY_PENDING");
        assertDb("CONFIRMING", 0, 0);
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void confirmedShortageCommitsCancelingBeforeExternalCancel() {
        shortage();
        when(toss.cancel(anyString(), anyString(), anyString())).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(i.<String>getArgument(2)).isEqualTo("cancel:" + approvalKey);
            assertDb("CANCELING", 0, 0);
            tx.executeWithoutResult(s -> {
                // 취소가 끝나기 전에는 만료된 주문도 계속 재고를 점유한다.
                order().setExpiresAt(LocalDateTime.now().minusMinutes(1));
                em.find(Ticket.class, ticketId).setTotal_ticket(1);
                assertThat(orders.sumActiveQuantity(ticketId, Arrays.asList(OrderStatus.values()),
                        LocalDateTime.now())).isEqualTo(1L);
            });
            var anotherOrder = new CreateOrderRequest();
            anotherOrder.setTicketId(ticketId);
            anotherOrder.setQuantity(1);
            assertThatThrownBy(() -> orderService.createOrder(anotherOrder, userId))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("TICKET_SOLD_OUT"));
            assertError("INVALID_ORDER_STATUS"); // 취소 중 중복 승인 요청 차단
            return response("CANCELED");
        });
        assertError("PAYMENT_CANCELED");
        assertDb("CANCELED", 0, 0);
        tx.executeWithoutResult(s -> assertThat(orders.sumActiveQuantity(ticketId,
                Arrays.asList(OrderStatus.values()), LocalDateTime.now())).isZero());
        var nextOrder = new CreateOrderRequest();
        nextOrder.setTicketId(ticketId);
        nextOrder.setQuantity(1);
        assertThat(orderService.createOrder(nextOrder, userId)).isNotNull();
        verify(toss, times(1)).confirm(anyString(), anyString(), anyLong(), anyString());
        verify(toss, times(1)).cancel(eq(request.paymentKey()), anyString(), eq("cancel:" + approvalKey));
    }

    @Test
    void invalidInventoryDataRequiresReviewInsteadOfCancel() {
        tx.executeWithoutResult(s -> em.find(Ticket.class, ticketId).setSold_ticket(-1));
        assertError("PAYMENT_RECOVERY_PENDING");
        assertDb("CONFIRMING", 0, -1);
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void missingTicketCancelsOnlyAfterOrderIsLockedAndChecked() {
        tx.executeWithoutResult(s -> em.remove(em.find(Ticket.class, ticketId)));
        when(toss.cancel(eq(request.paymentKey()), anyString(), eq("cancel:" + approvalKey)))
                .thenReturn(response("CANCELED"));
        assertError("PAYMENT_CANCELED");
        tx.executeWithoutResult(s -> {
            assertThat(order().getStatus().name()).isEqualTo("CANCELED");
            assertThat(payments.findByOrderOrderId(request.orderId())).isEmpty();
        });
        verify(toss, times(1)).cancel(eq(request.paymentKey()), anyString(), eq("cancel:" + approvalKey));
    }

    @ParameterizedTest
    @ValueSource(strings = {"network", "mismatch"})
    void uncertainCancellationKeepsReservation(String failure) {
        shortage();
        when(toss.cancel(anyString(), anyString(), anyString())).thenAnswer(i -> {
            if (failure.equals("network")) throw new IllegalStateException("cancel response lost");
            return new TossPaymentsClient.TossPaymentResponse(request.paymentKey(), "wrong", "CANCELED", "카드", 10000L, null);
        });
        assertError("PAYMENT_RECOVERY_PENDING");
        assertDb("CANCELING", 0, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELING", "CANCELED"})
    void cancellationCommitFailureDoesNotReleaseReservation(String failingStatus) {
        shortage();
        when(toss.cancel(anyString(), anyString(), anyString())).thenReturn(response("CANCELED"));
        doAnswer(i -> {
            Object found = mockingDetails(i.getMock()).getMockCreationSettings().getDefaultAnswer().answer(i);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    if (order().getStatus().name().equals(failingStatus)) {
                        em.flush();
                        throw new IllegalStateException("injected " + failingStatus + " commit failure");
                    }
                }
            });
            return found;
        }).when(orders).findByOrderIdAndUserIdForUpdate(request.orderId(), userId);
        assertError("PAYMENT_RECOVERY_PENDING");
        assertDb(failingStatus.equals("CANCELING") ? "CONFIRMING" : "CANCELING", 0, 0);
        verify(toss, times(failingStatus.equals("CANCELING") ? 0 : 1)).cancel(anyString(), anyString(), anyString());
    }

    @Test
    void exceptionAfterActualCommitReturnsStoredSuccess() {
        doAnswer(i -> {
            Object saved = mockingDetails(i.getMock()).getMockCreationSettings().getDefaultAnswer().answer(i);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { throw new IllegalStateException("after commit failure"); }
            });
            return saved;
        }).when(payments).save(any(Payment.class));
        assertThat(service.confirm(userId, request).status()).isEqualTo("DONE");
        assertDb("PAID", 1, 1);
        verify(payments, times(1)).save(any(Payment.class));
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    private void shortage() {
        tx.executeWithoutResult(s -> em.find(Ticket.class, ticketId).setTotal_ticket(0));
    }

    private TossPaymentsClient.TossPaymentResponse response(String status) {
        return new TossPaymentsClient.TossPaymentResponse(request.paymentKey(), request.orderId(), status, "카드", 10000L, null);
    }

    private Order order() {
        return em.createQuery("select o from Order o where o.orderId = :id", Order.class)
                .setParameter("id", request.orderId()).getSingleResult();
    }

    private void assertError(String code) {
        assertThatThrownBy(() -> service.confirm(userId, request)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private void assertDb(String status, long count, int sold) {
        tx.executeWithoutResult(s -> {
            assertThat(order().getStatus().name()).isEqualTo(status);
            assertThat(em.createQuery("select count(p) from Payment p where p.order.orderId = :id", Long.class)
                    .setParameter("id", request.orderId()).getSingleResult()).isEqualTo(count);
            assertThat(em.find(Ticket.class, ticketId).getSold_ticket()).isEqualTo(sold);
        });
    }
}
