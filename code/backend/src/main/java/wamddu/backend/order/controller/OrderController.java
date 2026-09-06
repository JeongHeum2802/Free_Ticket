package wamddu.backend.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import wamddu.backend.global.response.SuccessResponse;
import wamddu.backend.order.dto.request.CreateOrderRequestDTO;
import wamddu.backend.order.dto.response.CreateOrderResponseDTO;
import wamddu.backend.order.service.OrderService;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/orders")
    public SuccessResponse<CreateOrderResponseDTO> createOrder(@RequestBody CreateOrderRequestDTO requestDTO,
                                       @AuthenticationPrincipal UserDetails userDetails) {

        CreateOrderResponseDTO response = orderService.createOrder(requestDTO, userDetails);
        return SuccessResponse.success("주문이 생성되었습니다.",  response);
    }

    @GetMapping("/orders/{orderId}/checkout")
    public ResponseEntity<Map<String, Object>> checkout(
            @PathVariable("orderId") String orderId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return orderService.orderCheckOut(orderId, userDetails);
    }


    @GetMapping("/orders/me/reservations")
    public ResponseEntity<Map<String, Object>> getMyReservations(@AuthenticationPrincipal UserDetails userDetails) {
        return orderService.getMyReservations(userDetails);
    }
}
