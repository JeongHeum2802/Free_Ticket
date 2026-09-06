package wamddu.backend.payment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wamddu.backend.global.response.SuccessResponse;
import wamddu.backend.payment.dto.request.PaymentConfirmRequestDTO;
import wamddu.backend.payment.dto.response.PaymentConfirmResponseDTO;
import wamddu.backend.payment.service.PaymentService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payments/confirm")
    public SuccessResponse<PaymentConfirmResponseDTO> confirmPayment(
            @RequestBody PaymentConfirmRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        PaymentConfirmResponseDTO response = paymentService.confirmPayment(request, userDetails);

        return SuccessResponse.success("결제가 승인되었습니다.", response);
    }
}
