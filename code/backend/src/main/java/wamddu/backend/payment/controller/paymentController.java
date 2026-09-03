package wamddu.backend.payment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wamddu.backend.order.repository.orderRepository;
import wamddu.backend.order.service.orderService;
import wamddu.backend.payment.domain.paymentConfirmRequestDTO;
import wamddu.backend.payment.service.paymentService;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class paymentController {

    private final paymentService paymentService;

    @PostMapping("/payments/confirm")
    public ResponseEntity<Map<String, Object>> confirmPayment(
            @RequestBody paymentConfirmRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return paymentService.confirmPayment(request, userDetails);
    }
}
