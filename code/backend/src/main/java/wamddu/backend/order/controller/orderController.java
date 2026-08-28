package wamddu.backend.order.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import wamddu.backend.order.domain.createOrderRequestDTO;
import wamddu.backend.order.service.orderService;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class orderController {
    private final orderService orderService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(
            @Valid @RequestBody createOrderRequestDTO request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "주문이 생성되었습니다.",
                "data", orderService.createOrder(request, userId(principal))
        ));
    }

    @GetMapping("/{orderId}/checkout")
    public Map<String, Object> getCheckoutOrder(
            @PathVariable String orderId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return Map.of(
                "message", "결제할 주문을 조회했습니다.",
                "data", orderService.getCheckoutOrder(orderId, userId(principal))
        );
    }

    @GetMapping("/me/reservations")
    public Map<String, Object> getMyReservations(@AuthenticationPrincipal UserDetails principal) {
        return Map.of(
                "message", "예매 내역을 조회했습니다.",
                "data", Map.of("reservations", orderService.getMyReservations(userId(principal)))
        );
    }

    private Long userId(UserDetails principal) {
        return Long.parseLong(principal.getUsername());
    }
}
