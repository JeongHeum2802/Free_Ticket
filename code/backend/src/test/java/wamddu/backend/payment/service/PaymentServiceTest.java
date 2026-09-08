package wamddu.backend.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

    @InjectMocks
    private PaymentService paymentService;

    private User user;
    private Order order;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        user = new User(1L, "홍길동", "password123!", "user@example.com", "01012345678", "customer_123", Role.USER);

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
}
