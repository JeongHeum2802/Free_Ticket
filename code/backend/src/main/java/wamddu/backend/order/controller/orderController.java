package wamddu.backend.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import wamddu.backend.order.domain.createOrderRequestDTO;
import wamddu.backend.order.service.orderService;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class orderController {

    private final orderService orderService;

    @PostMapping("/api/orders")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody createOrderRequestDTO requestDTO,
                                                           @AuthenticationPrincipal UserDetails userDetails) {
        return orderService.createOrder(requestDTO, userDetails);
    }

    @GetMapping("/api/orders/{orderId}/checkout")
    public ResponseEntity<Map<String, Object>> checkout(
            @PathVariable("orderId") String orderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return orderService.orderCheckOut(orderId, userDetails);
    }
}
