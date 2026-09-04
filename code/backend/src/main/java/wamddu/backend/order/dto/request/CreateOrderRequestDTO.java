package wamddu.backend.order.dto.request;

import lombok.Getter;

@Getter
public class CreateOrderRequestDTO {
    private Long ticketId;
    private Integer quantity;
}
