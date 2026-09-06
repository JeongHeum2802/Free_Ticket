package wamddu.backend.payment.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import wamddu.backend.global.response.BusinessException;
import wamddu.backend.global.response.ErrorCode;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.OrderStatus;
import wamddu.backend.order.repository.OrderRepository;
import wamddu.backend.payment.component.TossPaymentClient;
import wamddu.backend.payment.domain.Payment;
import wamddu.backend.payment.dto.request.PaymentConfirmRequestDTO;
import wamddu.backend.payment.dto.response.ConfirmResponseDTO;
import wamddu.backend.payment.dto.response.PaymentConfirmResponseDTO;
import wamddu.backend.payment.repository.PaymentRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.TicketRepository;
import wamddu.backend.user.repository.UserRepository;

import java.time.LocalDateTime;

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
    public PaymentConfirmResponseDTO confirmPayment(
            PaymentConfirmRequestDTO request,
            UserDetails userDetails
    ) {

//        User user = userRepository.findById(Long.parseLong(userDetails.getUsername())).orElse(null);
//        if(user == null){
//            throw new BusinessException(ErrorCode.UNAUTHORIZED);
//        }

        Order order = orderRepository.findByOrderId(request.getOrderId());
        if(order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

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

        if(!request.getAmount().equals(order.getAmount())) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT);
        }

        ConfirmResponseDTO tossResponse = tossPaymentClient.sendConfirmRequest(request);

        if((!tossResponse.getOrderId().equals(order.getOrderId()))
                || (!tossResponse.getStatus().equals("DONE"))
                || (!tossResponse.getTotalAmount().equals(order.getAmount()))) {

            log.warn("결제 자동 취소를 진행합니다. paymentKey:{}", tossResponse.getPaymentKey());
            tossPaymentClient.cancelPayment(tossResponse.getPaymentKey(), "서버 주문 검증 실패");
            order.setStatus(OrderStatus.PAYMENT_FAILED);

            Ticket ticket = ticketRepository.findById(order.getTicket_id()).orElse(null);
            ticket.setSold_ticket(ticket.getSold_ticket() - order.getQuantity());
            ticketRepository.save(ticket);

            throw new BusinessException(ErrorCode.TOSS_CONFIRM_FAILED);
        }

        order.setPaymentKey(tossResponse.getPaymentKey());
        order.setStatus(OrderStatus.PAID);

        Payment payment = Payment.create(tossResponse);
        paymentRepository.save(payment);

        order.setPayment(payment);
        orderRepository.save(order);

        return PaymentConfirmResponseDTO.create(tossResponse);
    }
}
