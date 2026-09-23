package wamddu.backend.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.payment.client.TossPaymentsClient;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.dto.request.ConfirmPaymentRequest;
import wamddu.backend.payment.dto.response.PaymentResponse;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.TicketRepository;
import wamddu.backend.user.domain.Role;
import wamddu.backend.user.domain.User;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TossPaymentsClient tossPaymentsClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private PaymentService paymentService;

    private User user;
    private Order order;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("홍길동")
                .password("password123!")
                .email("user@example.com")
                .phonenumber("01012345678")
                .customerKey("customer_123")
                .role(Role.USER)
                .build();

        order = new Order();
        order.setId(10L);
        order.setOrderId("ORD-20260907-001");
        order.setTicket_id(100L);
        order.setUser(user);
        order.setQuantity(2);
        order.setTotalAmount(300000L);
        order.setStatus(OrderStatus.PENDING);
        order.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        order.setIdempotencyKey("idempotency_key_123");

        ticket = new Ticket();
        ticket.setId(100L);
        ticket.setTotal_ticket(100);
        ticket.setSold_ticket(10);
    }

    @Test
    @DisplayName("결제 승인 성공 테스트")
    void confirm_Success() {
        // given
        ConfirmPaymentRequest request = new ConfirmPaymentRequest("payment_key_123", "ORD-20260907-001", 300000L);

        given(orderRepository.findByOrderIdAndUserIdForUpdate("ORD-20260907-001", 1L))
                .willReturn(Optional.of(order));
        given(paymentRepository.existsByPaymentKey("payment_key_123")).willReturn(false);

        TossPaymentsClient.TossPaymentResponse tossResponse = new TossPaymentsClient.TossPaymentResponse(
                "payment_key_123", "ORD-20260907-001", "DONE", "카드", 300000L,
                new TossPaymentsClient.TossPaymentResponse.Receipt("http://example.com/receipt")
        );

        given(tossPaymentsClient.confirm("payment_key_123", "ORD-20260907-001", 300000L, "idempotency_key_123"))
                .willReturn(tossResponse);
        given(ticketRepository.findByIdForUpdate(100L)).willReturn(Optional.of(ticket));

        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        // when
        PaymentResponse response = paymentService.confirm(1L, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo("ORD-20260907-001");
        assertThat(response.amount()).isEqualTo(300000L);
        assertThat(response.status()).isEqualTo("DONE");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(ticket.getSold_ticket()).isEqualTo(12); // 기존 10 + 주문 2
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("결제 승인 실패 - 결제 금액 불일치")
    void confirm_AmountMismatch_ThrowsApiException() {
        // given
        ConfirmPaymentRequest request = new ConfirmPaymentRequest("payment_key_123", "ORD-20260907-001", 100000L); // 주문금액 30만, 요청금액 10만

        given(orderRepository.findByOrderIdAndUserIdForUpdate("ORD-20260907-001", 1L))
                .willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentService.confirm(1L, request))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("AMOUNT_MISMATCH");
    }

    @Test
    @DisplayName("결제 승인 실패 - 이미 사용된 결제 키")
    void confirm_PaymentKeyAlreadyUsed_ThrowsApiException() {
        // given
        ConfirmPaymentRequest request = new ConfirmPaymentRequest("already_used_key", "ORD-20260907-001", 300000L);

        given(orderRepository.findByOrderIdAndUserIdForUpdate("ORD-20260907-001", 1L))
                .willReturn(Optional.of(order));
        given(paymentRepository.existsByPaymentKey("already_used_key")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> paymentService.confirm(1L, request))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("PAYMENT_KEY_ALREADY_USED");
    }

    @Test
    @DisplayName("결제 승인 실패 - 만료된 주문")
    void confirm_OrderExpired_ThrowsApiException() {
        // given
        order.setExpiresAt(LocalDateTime.now().minusMinutes(1)); // 만료
        ConfirmPaymentRequest request = new ConfirmPaymentRequest("payment_key_123", "ORD-20260907-001", 300000L);

        given(orderRepository.findByOrderIdAndUserIdForUpdate("ORD-20260907-001", 1L))
                .willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentService.confirm(1L, request))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("ORDER_EXPIRED");
    }

    @ParameterizedTest
    @CsvSource({
            "wrong_key, 300000, 300000",
            "payment_key_123, 100000, 300000",
            "payment_key_123, 300000, 100000"
    })
    @DisplayName("커밋 예외 후 PAID여도 결제 키 또는 결제·주문 금액이 다르면 성공으로 반환하지 않는다")
    void recovery_RejectsMismatchedPaidPayment(String paymentKey, long paymentAmount, long orderAmount) {
        Payment payment = Payment.createPayment(order, paymentKey, paymentAmount,
                "카드", "DONE", LocalDateTime.now(), null);
        simulateCommitFailure(OrderStatus.PAID, orderAmount, Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirm(1L,
                new ConfirmPaymentRequest("payment_key_123", order.getOrderId(), 300000L)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("PAYMENT_RECOVERY_PENDING");
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("커밋 예외 후 PAID의 결제 키와 금액이 일치하면 기존 결제를 반환한다")
    void recovery_ReturnsMatchingPaidPayment() {
        Payment payment = Payment.createPayment(order, "payment_key_123", 300000L,
                "카드", "DONE", LocalDateTime.now(), null);
        simulateCommitFailure(OrderStatus.PAID, 300000L, Optional.of(payment));

        PaymentResponse result = paymentService.confirm(1L,
                new ConfirmPaymentRequest("payment_key_123", order.getOrderId(), 300000L));

        assertThat(result).isEqualTo(PaymentResponse.from(payment));
        verify(paymentRepository, times(1)).save(any(Payment.class));
        assertThat(ticket.getSold_ticket()).isEqualTo(12);
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("커밋 예외 후 CONFIRMING인데 Payment가 있으면 완료 처리를 재시도하지 않는다")
    void recovery_DoesNotRetryWhenConfirmingHasPayment() {
        Payment payment = Payment.createPayment(order, "payment_key_123", 300000L,
                "카드", "DONE", LocalDateTime.now(), null);
        simulateCommitFailure(OrderStatus.CONFIRMING, 300000L, Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirm(1L,
                new ConfirmPaymentRequest("payment_key_123", order.getOrderId(), 300000L)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("PAYMENT_RECOVERY_PENDING");
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(ticketRepository, times(1)).findByIdForUpdate(100L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMING);
        assertThat(ticket.getSold_ticket()).isEqualTo(10);
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyString());
    }

    private void simulateCommitFailure(OrderStatus recoveredStatus, long recoveredAmount,
                                       Optional<Payment> storedPayment) {
        given(orderRepository.findByOrderIdAndUserIdForUpdate(order.getOrderId(), 1L))
                .willReturn(Optional.of(order));
        given(tossPaymentsClient.confirm("payment_key_123", order.getOrderId(), 300000L, order.getIdempotencyKey()))
                .willReturn(new TossPaymentsClient.TossPaymentResponse(
                        "payment_key_123", order.getOrderId(), "DONE", "카드", 300000L, null));
        given(ticketRepository.findByIdForUpdate(100L)).willReturn(Optional.of(ticket));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(paymentRepository.findByOrderOrderId(order.getOrderId())).willReturn(storedPayment);
        given(transactionManager.getTransaction(any())).willAnswer(invocation -> new SimpleTransactionStatus());
        var commits = new AtomicInteger();
        doAnswer(invocation -> {
            if (commits.incrementAndGet() == 2) {
                // 단위 테스트에서는 커밋 예외 이후 DB에서 조회할 상태를 명시적으로 대체한다.
                order.setStatus(recoveredStatus);
                order.setTotalAmount(recoveredAmount);
                ticket.setSold_ticket(recoveredStatus == OrderStatus.PAID ? 12 : 10);
                throw new IllegalStateException("Injected commit failure");
            }
            return null;
        }).when(transactionManager).commit(any());
    }
}
