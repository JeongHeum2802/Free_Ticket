package wamddu.backend.payment.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.payment.component.TossPaymentClient;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.dto.request.PaymentConfirmRequestDTO;
import wamddu.backend.payment.dto.response.PaymentConfirmResponseDTO;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.TicketRepository;
import wamddu.backend.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentRepository paymentRepository;

    @Transactional
    public ResponseEntity<Map<String, Object>> confirmPayment(
            PaymentConfirmRequestDTO request,
            UserDetails userDetails
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();

//        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElse(null);
//        if(user == null){
//            response.put("code", "UNAUTHORIZED");
//            response.put("message", "로그인이 필요한 서비스입니다.");
//
//            return  ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
//        }

        Order order = orderRepository.findByOrderId(request.getOrderId());
        if(order == null) {
            response.put("code", "ORDER_NOT_FOUND");
            response.put("message", "잘못된 OrderId입니다.");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

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

        if(!request.getAmount().equals(order.getAmount())) {
            response.put("code", "INVALID_AMOUNT");
            response.put("message", "수량이 다릅니다.");

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        PaymentConfirmResponseDTO tossResponse = tossPaymentClient.sendConfirmRequest(request);

        if((!tossResponse.getOrderId().equals(order.getOrderId()))
                || (!tossResponse.getStatus().equals("DONE"))
                || (!tossResponse.getTotalAmount().equals(order.getAmount()))) {
            response.put("code", "INVALID_TRANSACTION");
            response.put("message", "잘못된 거래입니다.");

            log.warn("결제 자동 취소를 진행합니다. paymentKey:{}", tossResponse.getPaymentKey());
            tossPaymentClient.cancelPayment(tossResponse.getPaymentKey(), "서버 주문 검증 실패");
            order.setStatus(OrderStatus.PAYMENT_FAILED);

            Ticket ticket = ticketRepository.findById(order.getTicket_id()).orElse(null);
            ticket.setSold_ticket(ticket.getSold_ticket() - order.getQuantity());
            ticketRepository.save(ticket);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        order.setPaymentKey(tossResponse.getPaymentKey());
        order.setStatus(OrderStatus.PAID);

        Payment payment = new Payment();
        payment.setPaymentKey(order.getPaymentKey());
        payment.setMethod(tossResponse.getMethod());
        payment.setStatus(tossResponse.getStatus());
        payment.setApprovedAt(tossResponse.getApprovedAt());
        payment.setReceiptUrl(tossResponse.getReceiptUrl());
        paymentRepository.save(payment);

        order.setPayment(payment);
        orderRepository.save(order);

        response.put("message", "결제가 승인되었습니다.");
        data.put("orderId", tossResponse.getOrderId());
        data.put("paymentKey", tossResponse.getPaymentKey());
        data.put("amount", tossResponse.getTotalAmount());
        data.put("method", tossResponse.getMethod());
        data.put("status", tossResponse.getStatus());
        data.put("approvedAt",  tossResponse.getApprovedAt());
        data.put("receiptUrl", tossResponse.getReceiptUrl());
        response.put("data", data);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
