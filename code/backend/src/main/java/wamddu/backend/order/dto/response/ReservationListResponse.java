package wamddu.backend.order.dto.response;

import java.util.List;

public record ReservationListResponse(
        List<ReservationHistoryResponse> reservations
) {
}
