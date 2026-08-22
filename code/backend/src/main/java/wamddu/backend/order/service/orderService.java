package wamddu.backend.order.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import wamddu.backend.order.domain.Order;
import wamddu.backend.order.domain.createOrderRequestDTO;
import wamddu.backend.order.repository.orderRepository;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.ticket.repository.ticketRepository;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class orderService {

    private final orderRepository orderRepository;
    private final ticketRepository ticketRepository;

    @Transactional
    public ResponseEntity<Map<String, Object>> createOrder(createOrderRequestDTO requestDTO, UserDetails userDetails) {

        Map<String, Object> response = new LinkedHashMap<>();

        //존재하는 티켓인지 확인
        Ticket ticket = ticketRepository.findById(requestDTO.getTicketId()).orElse(null);
        if(ticket == null) {
            response.put("code", "TICKET_NOT_FOUND");
            response.put("message", "존재하지 않는 티켓입니다.");

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

        }
    }
}
