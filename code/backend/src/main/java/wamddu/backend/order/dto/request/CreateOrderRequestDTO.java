package wamddu.backend.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderRequestDTO {
    @NotNull
    private Long ticketId;

    @NotNull
    @Min(1)
    @Max(10)
    private Integer quantity;
}
