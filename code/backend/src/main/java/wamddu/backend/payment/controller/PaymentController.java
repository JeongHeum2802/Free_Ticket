package wamddu.backend.payment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import wamddu.backend.global.response.ApiResponse;
import wamddu.backend.payment.dto.request.ConfirmPaymentRequest;
import wamddu.backend.payment.dto.response.PaymentResponse;
import wamddu.backend.payment.service.PaymentService;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/confirm")
    public ApiResponse<PaymentResponse> confirm(
            @Valid @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ApiResponse.success("결제가 승인되었습니다.", paymentService.confirm(Long.parseLong(principal.getUsername()), request));
    }
}
