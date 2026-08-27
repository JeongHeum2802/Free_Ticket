package wamddu.backend.order.domain;

import lombok.Getter;

@Getter
public class createOrderRequestDTO {
    private Long ticketId;
    private Integer quantity;
}
