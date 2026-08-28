package wamddu.backend.payment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import wamddu.backend.payment.domain.ConfirmPaymentRequest;
import wamddu.backend.payment.service.PaymentService;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/confirm")
    public Map<String, Object> confirm(
            @Valid @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return Map.of(
                "message", "결제가 승인되었습니다.",
                "data", paymentService.confirm(Long.parseLong(principal.getUsername()), request)
        );
    }
}
