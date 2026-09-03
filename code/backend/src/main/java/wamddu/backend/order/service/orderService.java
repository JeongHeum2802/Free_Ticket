package wamddu.backend.order.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import wamddu.backend.event.domain.Event;
import wamddu.backend.event.repository.eventRepository;
import wamddu.backend.payment.component.TossPaymentClient;
import wamddu.backend.order.domain.*;
import wamddu.backend.order.repository.orderRepository;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.repository.paymentRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.ticketRepository;
import wamddu.backend.user.domain.User;
import wamddu.backend.user.repository.userRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class orderService {

    private final orderRepository orderRepository;
    private final ticketRepository ticketRepository;
    private final userRepository userRepository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final TossPaymentClient tossPaymentClient;
    private final paymentRepository paymentRepository;
    private final eventRepository eventRepository;

    public static String generateOrderId()
    {
        String dateStr = LocalDateTime.now().format(FORMATTER);
        String randomStr = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 6).toUpperCase();

        return "ORD-" +  dateStr + "-" + randomStr;
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> createOrder(createOrderRequestDTO requestDTO, UserDetails userDetails) {

        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();

        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElse(null);

        //유효한 사용자인지 확인
        if(user == null){
            response.put("code", "UNAUTHORIZED");
            response.put("message", "로그인이 필요한 서비스입니다.");

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        //존재하는 티켓인지 확인
        Ticket ticket = ticketRepository.findById(requestDTO.getTicketId()).orElse(null);
        if(ticket == null) {
            response.put("code", "TICKET_NOT_FOUND");
            response.put("message", "존재하지 않는 티켓입니다.");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        //티켓 10장 이하 구매인지 확인
        if(!(requestDTO.getQuantity() >= 1 && requestDTO.getQuantity() <= 10)) {
            response.put("code", "QUANTITY_ERROR");
            response.put("message", "티켓은 최소 1장 최대 10장까지 구매 가능합니다.");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        //티켓이 남아있는지 확인
        int remaining = ticket.getTotal_ticket() - ticket.getSold_ticket();
        if(remaining <= requestDTO.getQuantity()) {
            response.put("code", "TICKET_NOT_REMAINING");
            response.put("message", "티켓 수량이 부족합니다.");

            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        try {
            ticket.setSold_ticket(ticket.getSold_ticket() + requestDTO.getQuantity());
            ticketRepository.save(ticket);

            Order order = new Order();
            order.setOrderId(generateOrderId());
            order.setTicket_id(ticket.getId());
            order.setEvent_id(ticket.getEvent().getId());
            order.setUser(user);
            order.setQuantity(requestDTO.getQuantity());
            order.setAmount(requestDTO.getQuantity() * ticket.getPrice());
            orderRepository.save(order);

            response.put("message", "주문이 생성되었습니다.");

            data.put("orderId",  order.getOrderId());
            data.put("orderName", ticket.getType());
            data.put("amount", order.getAmount());
            data.put("quantity", order.getQuantity());
            data.put("customerKey", user.getCustomerKey());
            data.put("customerName", user.getUsername());
            data.put("customerEmail", user.getEmail());
            data.put("expiresAt", order.getExpiresAt());

            response.put("data", data);

            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch(Exception e) {
            response.put("code", "INTERNAL_SERVER_ERROR");
            response.put("message", e.getMessage());

            log.error(e.getMessage());

            throw e;
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> orderCheckOut(String orderId, UserDetails userDetails) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();

        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElse(null);
        if(user == null){
            response.put("code", "UNAUTHORIZED");
            response.put("message", "로그인이 필요한 서비스입니다.");

            return  ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        Order order = orderRepository.findByOrderId(orderId);
        if(order.getStatus() != OrderStatus.PENDING) {
            response.put("code", "ORDER_NOT_PENDING");
            response.put("message", "처리된 주문입니다.");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if(LocalDateTime.now().isAfter(order.getExpiresAt())) {
            order.setStatus(OrderStatus.EXPIRED);
            response.put("code", "ORDER_EXPIRED");
            response.put("message", "만료된 주문입니다.");

            Ticket ticket = ticketRepository.findById(order.getTicket_id()).orElse(null);
            ticket.setSold_ticket(ticket.getSold_ticket() - order.getQuantity());
            ticketRepository.save(ticket);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
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
            response.put("code", "UNAUTHORIZED");
            response.put("message", "로그인이 필요한 서비스입니다.");

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
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
