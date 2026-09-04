package wamddu.backend.order.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.repository.EventRepository;
import wamddu.backend.global.response.BusinessException;
import wamddu.backend.global.response.ErrorCode;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.dto.request.CreateOrderRequestDTO;
import wamddu.backend.order.dto.response.CreateOrderResponseDTO;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.TicketRepository;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final EventRepository eventRepository;

    public static String generateOrderId()
    {
        String dateStr = LocalDateTime.now().format(FORMATTER);
        String randomStr = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 6).toUpperCase();

        return "ORD-" +  dateStr + "-" + randomStr;
    }

    @Transactional
    public CreateOrderResponseDTO createOrder(CreateOrderRequestDTO requestDTO, UserDetails userDetails) {
        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElse(null);

        //유효한 사용자인지 확인
        if(user == null){
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        //존재하는 티켓인지 확인
        Ticket ticket = ticketRepository.findById(requestDTO.getTicketId()).orElse(null);
        if(ticket == null) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }

        //티켓 10장 이하 구매인지 확인
        if(!(requestDTO.getQuantity() >= 1 && requestDTO.getQuantity() <= 10)) {
            throw new BusinessException(ErrorCode.QUANTITY_ERROR);
        }

        //티켓이 남아있는지 확인
        int remaining = ticket.getTotal_ticket() - ticket.getSold_ticket();
        if(remaining < requestDTO.getQuantity()) {
            throw new BusinessException(ErrorCode.TICKET_NOT_REMAINING);
        }

        ticket.sell(requestDTO.getQuantity());

        Order order = Order.create(generateOrderId(), user, ticket, requestDTO.getQuantity());
        orderRepository.save(order);

        return CreateOrderResponseDTO.create(order, ticket, user);
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> orderCheckOut(String orderId, UserDetails userDetails) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();

        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElse(null);
        if(user == null){
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Order order = orderRepository.findByOrderId(orderId);
        if(order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_NOT_PENDING);
        }

        if(LocalDateTime.now().isAfter(order.getExpiresAt())) {
            order.setStatus(OrderStatus.EXPIRED);

            Ticket ticket = ticketRepository.findById(order.getTicket_id()).orElse(null);
            ticket.setSold_ticket(ticket.getSold_ticket() - order.getQuantity());
            ticketRepository.save(ticket);

            throw new BusinessException(ErrorCode.ORDER_EXPIRED);
        }

        response.put("message", "결제할 주문을 조회했습니다.");
        data.put("orderId", order.getOrderId());
        data.put("orderName", user.getUsername());
        data.put("amount", order.getAmount());
        data.put("quantity",  order.getQuantity());
        data.put("customerKey", user.getCustomerKey());
        data.put("customerName", user.getUsername());
        data.put("customerEmail", user.getEmail());
        data.put("expiresAt", order.getExpiresAt());
        response.put("data", data);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> getMyReservations(UserDetails userDetails) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();
        List<Object> reservations = new ArrayList<>();

        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElse(null);
        if(user == null) {
            throw new  BusinessException(ErrorCode.UNAUTHORIZED);
        }

        List<Order> orders = orderRepository.findByUserId(Long.parseLong(userDetails.getUsername()));
        for(Order order : orders) {
            if(order.getStatus() == OrderStatus.PAID) {
                Payment payment = order.getPayment();
                Event event = eventRepository.findById(order.getEvent_id()).orElse(null);
                Ticket ticket = ticketRepository.findById(order.getTicket_id()).orElse(null);

                Map<String, Object> reservation = new LinkedHashMap<>();
                reservation.put("orderId", order.getOrderId());
                reservation.put("eventId", order.getEvent_id());
                reservation.put("eventName", event.getName());
                reservation.put("mainImageUrl", event.getMainImageUrl());
                reservation.put("location", event.getLocation());
                reservation.put("ticketType", ticket.getType());
                reservation.put("performanceAt", ticket.getStart_time());
                reservation.put("quantity", order.getQuantity());
                reservation.put("amount", order.getAmount());
                reservation.put("paidAt", payment.getApprovedAt());
                reservation.put("paymentMethod", payment.getMethod());
                reservation.put("receiptUrl",  payment.getReceiptUrl());
                reservation.put("status", order.getStatus());

                reservations.add(reservation);
            }
        }

        response.put("message", "예매 내역을 조회했습니다.");
        data.put("reservations", reservations);
        response.put("data", data);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
