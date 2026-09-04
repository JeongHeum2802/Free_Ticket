package wamddu.backend.payment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wamddu.backend.payment.dto.request.PaymentConfirmRequestDTO;
import wamddu.backend.payment.service.PaymentService;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payments/confirm")
    public ResponseEntity<Map<String, Object>> confirmPayment(
            @RequestBody PaymentConfirmRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return paymentService.confirmPayment(request, userDetails);
    }
}
