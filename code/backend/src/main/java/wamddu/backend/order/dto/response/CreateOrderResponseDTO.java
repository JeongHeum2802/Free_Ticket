package wamddu.backend.order.dto.response;

import lombok.*;
import wamddu.backend.order.domain.Order;
import wamddu.backend.ticket.domain.Ticket;
import wamddu.backend.user.domain.User;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CreateOrderResponseDTO {
    private String orderId;
    private String orderName;
    private Integer amount;
    private Integer quantity;
    private String customerKey;
    private String customerName;
    private String customerEmail;
    private LocalDateTime expiresAt;

    public static CreateOrderResponseDTO create(Order order, Ticket ticket, User user) {
        return CreateOrderResponseDTO.builder()
                .orderId(order.getOrderId())
                .orderName(ticket.getType())
                .amount(order.getAmount())
                .quantity(order.getQuantity())
                .customerKey(user.getCustomerKey())
                .customerName(user.getUsername())
                .customerEmail(user.getEmail())
                .expiresAt(order.getExpiresAt())
                .build();
    }
}
