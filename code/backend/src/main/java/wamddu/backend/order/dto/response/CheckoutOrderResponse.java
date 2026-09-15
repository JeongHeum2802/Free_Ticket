package wamddu.backend.order.dto.response;

import wamddu.backend.order.domain.Order;
import wamddu.backend.ticket.domain.Ticket;

import java.time.LocalDateTime;

public record CheckoutOrderResponse(
        String orderId,
        String orderName,
        Long amount,
        Integer quantity,
        String customerKey,
        String customerName,
        String customerEmail,
        LocalDateTime expiresAt
) {
    public static CheckoutOrderResponse of(Order order, Ticket ticket) {
        return new CheckoutOrderResponse(
                order.getOrderId(),
                ticket.getType(),
                order.getTotalAmount(),
                order.getQuantity(),
                order.getUser().getCustomerKey(),
                order.getUser().getUsername(),
                order.getUser().getEmail(),
                order.getExpiresAt()
        );
    }
}
