package wamddu.backend.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import wamddu.backend.event.domain.Event;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.dto.request.CreateOrderRequestDTO;
import wamddu.backend.order.dto.response.CheckoutOrderResponse;
import wamddu.backend.order.dto.response.ReservationListResponse;
import wamddu.backend.order.repository.orderRepository;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.ticketRepository;
import wamddu.backend.user.domain.Role;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.repository.userRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private orderRepository orderRepository;

    @Mock
    private ticketRepository ticketRepository;

    @Mock
    private userRepository userRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private orderService orderService;

    private User user;
    private Event event;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        user = new User(1L, "홍길동", "password123!", "user@example.com", "01012345678", "customer_123", Role.USER);

        event = new Event();
        event.setId(101L);
        event.setName("햄릿 뮤지컬");
        event.setStartDate(LocalDate.now());
        event.setEndDate(LocalDate.now().plusDays(30));
        event.setLocation("세종문화회관");
        event.setBannerImageUrl("http://example.com/banner.jpg");
        event.setMainImageUrl("http://example.com/main.jpg");
        event.setCategory("musical");

        ticket = new Ticket();
        ticket.setId(1L);
        ticket.setType("VIP석");
        ticket.setPrice(150000);
        ticket.setTotal_ticket(100);
        ticket.setSold_ticket(10);
        ticket.setBookingEndtime(LocalDateTime.now().plusDays(5));
        ticket.setEvent(event);
    }

    @Test
    @DisplayName("주문 생성 성공 테스트")
    void createOrder_Success() {
        // given
        CreateOrderRequestDTO request = new CreateOrderRequestDTO();
        request.setTicketId(1L);
        request.setQuantity(2);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(ticketRepository.findByIdForUpdate(1L)).willReturn(Optional.of(ticket));
        given(orderRepository.sumActiveQuantity(eq(1L), any(), any())).willReturn(0L);

        // when
        CheckoutOrderResponse response = orderService.createOrder(request, 1L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.amount()).isEqualTo(300000L);
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.orderName()).isEqualTo("VIP석");
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("주문 생성 실패 - 티켓 잔여 수량 부족")
    void createOrder_SoldOut_ThrowsApiException() {
        // given
        CreateOrderRequestDTO request = new CreateOrderRequestDTO();
        request.setTicketId(1L);
        request.setQuantity(100); // 100개 요청, 잔여 수량 90개

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(ticketRepository.findByIdForUpdate(1L)).willReturn(Optional.of(ticket));
        given(orderRepository.sumActiveQuantity(eq(1L), any(), any())).willReturn(0L);

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(request, 1L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("TICKET_SOLD_OUT");
    }

    @Test
    @DisplayName("주문 생성 실패 - 예매 마감된 티켓")
    void createOrder_BookingClosed_ThrowsApiException() {
        // given
        ticket.setBookingEndtime(LocalDateTime.now().minusDays(1)); // 마감됨
        CreateOrderRequestDTO request = new CreateOrderRequestDTO();
        request.setTicketId(1L);
        request.setQuantity(1);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(ticketRepository.findByIdForUpdate(1L)).willReturn(Optional.of(ticket));

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(request, 1L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("BOOKING_CLOSED");
    }

    @Test
    @DisplayName("체크아웃 주문 조회 성공 테스트")
    void getCheckoutOrder_Success() {
        // given
        Order order = new Order();
        order.setOrderId("ORD-20260907-001");
        order.setTicket_id(1L);
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        order.setTotalAmount(150000L);
        order.setQuantity(1);

        given(orderRepository.findByOrderIdAndUserId("ORD-20260907-001", 1L)).willReturn(Optional.of(order));
        given(ticketRepository.findById(1L)).willReturn(Optional.of(ticket));

        // when
        CheckoutOrderResponse response = orderService.getCheckoutOrder("ORD-20260907-001", 1L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo("ORD-20260907-001");
        assertThat(response.amount()).isEqualTo(150000L);
    }

    @Test
    @DisplayName("체크아웃 주문 조회 실패 - 만료된 주문")
    void getCheckoutOrder_Expired_ThrowsApiException() {
        // given
        Order order = new Order();
        order.setOrderId("ORD-20260907-EXPIRED");
        order.setTicket_id(1L);
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setExpiresAt(LocalDateTime.now().minusMinutes(1)); // 만료됨

        given(orderRepository.findByOrderIdAndUserId("ORD-20260907-EXPIRED", 1L)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.getCheckoutOrder("ORD-20260907-EXPIRED", 1L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("ORDER_EXPIRED");
    }

    @Test
    @DisplayName("내 예매 내역 조회 성공 테스트")
    void getMyReservations_Success() {
        // given
        Order order = new Order();
        order.setOrderId("ORD-20260907-PAID");
        order.setTicket_id(1L);
        order.setQuantity(1);
        order.setPaidAt(LocalDateTime.now());
        order.setStatus(OrderStatus.PAID);

        Payment payment = new Payment();
        payment.setId(10L);
        payment.setOrder(order);
        payment.setAmount(150000L);
        payment.setMethod("카드");
        payment.setReceiptUrl("http://example.com/receipt");

        given(paymentRepository.findAllPaidByUserId(1L, OrderStatus.PAID)).willReturn(List.of(payment));
        given(ticketRepository.findById(1L)).willReturn(Optional.of(ticket));

        // when
        ReservationListResponse response = orderService.getMyReservations(1L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.reservations()).hasSize(1);
        assertThat(response.reservations().get(0).orderId()).isEqualTo("ORD-20260907-PAID");
        assertThat(response.reservations().get(0).amount()).isEqualTo(150000L);
    }
}
