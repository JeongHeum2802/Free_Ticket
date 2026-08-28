package wamddu.backend.order.domain;

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
}
