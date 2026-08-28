package wamddu.backend.order.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class createOrderRequestDTO {
    @NotNull
    private Long ticketId;

    @NotNull
    @Min(1)
    @Max(10)
    private Integer quantity;
}
