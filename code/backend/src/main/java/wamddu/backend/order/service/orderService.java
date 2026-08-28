package wamddu.backend.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.order.domain.*;
import wamddu.backend.order.repository.orderRepository;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.ticketRepository;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.repository.userRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class orderService {
    private static final DateTimeFormatter ORDER_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int PAYMENT_WINDOW_MINUTES = 10;

    private final orderRepository orderRepository;
    private final ticketRepository ticketRepository;
    private final userRepository userRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public CheckoutOrderResponse createOrder(createOrderRequestDTO request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."));
        Ticket ticket = ticketRepository.findByIdForUpdate(request.getTicketId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "존재하지 않는 티켓입니다."));

        LocalDateTime now = LocalDateTime.now();
        if (ticket.getBookingEndtime() == null || !now.isBefore(ticket.getBookingEndtime())) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_CLOSED", "예매가 마감된 티켓입니다.");
        }
        if (ticket.getPrice() == null || ticket.getPrice() < 0
                || ticket.getTotal_ticket() == null || ticket.getSold_ticket() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TICKET_DATA", "티켓 판매 정보가 올바르지 않습니다.");
        }

        long pendingQuantity = orderRepository.sumActiveQuantity(
                ticket.getId(), List.of(OrderStatus.PENDING, OrderStatus.CONFIRMING), now);
        long remaining = (long) ticket.getTotal_ticket() - ticket.getSold_ticket() - pendingQuantity;
        if (remaining < request.getQuantity()) {
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_SOLD_OUT", "선택한 수량만큼 남은 티켓이 없습니다.");
        }

        if (user.getCustomerKey() == null || user.getCustomerKey().isBlank()) {
            user.setCustomerKey("customer_" + compactUuid());
        }

        Order order = new Order();
        order.setOrderId(generateOrderId());
        order.setTicket_id(ticket.getId());
        order.setEvent_id(ticket.getEvent().getId());
        order.setUser(user);
        order.setQuantity(request.getQuantity());
        order.setUnitPrice(ticket.getPrice());
        order.setTotalAmount((long) ticket.getPrice() * request.getQuantity());
        order.setStatus(OrderStatus.PENDING);
        order.setOrderDate(now);
        order.setExpiresAt(now.plusMinutes(PAYMENT_WINDOW_MINUTES));
        order.setIdempotencyKey(UUID.randomUUID().toString());
        orderRepository.save(order);

        return toCheckoutResponse(order, ticket);
    }

    @Transactional(readOnly = true)
    public CheckoutOrderResponse getCheckoutOrder(String orderId, Long userId) {
        Order order = orderRepository.findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS", "결제를 진행할 수 없는 주문입니다.");
        }
        if (LocalDateTime.now().isAfter(order.getExpiresAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_EXPIRED", "결제 가능 시간이 만료되었습니다.");
        }
        Ticket ticket = ticketRepository.findById(order.getTicket_id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "티켓을 찾을 수 없습니다."));
        return toCheckoutResponse(order, ticket);
    }

    @Transactional(readOnly = true)
    public List<ReservationHistoryResponse> getMyReservations(Long userId) {
        return paymentRepository.findAllPaidByUserId(userId, OrderStatus.PAID)
                .stream()
                .map(this::toReservationResponse)
                .toList();
    }

    private CheckoutOrderResponse toCheckoutResponse(Order order, Ticket ticket) {
        return new CheckoutOrderResponse(
                order.getOrderId(),
                ticket.getEvent().getName() + " - " + ticket.getType(),
                order.getTotalAmount(),
                order.getQuantity(),
                order.getUser().getCustomerKey(),
                order.getUser().getUsername(),
                order.getUser().getEmail(),
                order.getExpiresAt()
        );
    }

    private ReservationHistoryResponse toReservationResponse(Payment payment) {
        Order order = payment.getOrder();
        Ticket ticket = ticketRepository.findById(order.getTicket_id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "티켓을 찾을 수 없습니다."));
        return new ReservationHistoryResponse(
                order.getOrderId(),
                ticket.getEvent().getId(),
                ticket.getEvent().getName(),
                ticket.getEvent().getMainImageUrl(),
                ticket.getEvent().getLocation(),
                ticket.getType(),
                ticket.getStart_time(),
                order.getQuantity(),
                payment.getAmount(),
                order.getPaidAt(),
                payment.getMethod(),
                payment.getReceiptUrl(),
                order.getStatus().name()
        );
    }

    private static String generateOrderId() {
        String timestamp = LocalDateTime.now().format(ORDER_ID_FORMAT);
        return "ORD-" + timestamp + "-" + compactUuid().substring(0, 8).toUpperCase();
    }

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
