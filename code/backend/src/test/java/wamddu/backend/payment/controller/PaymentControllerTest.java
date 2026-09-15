package wamddu.backend.payment.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import wamddu.backend.global.exception.ApiException;
import wamddu.backend.global.exception.ApiExceptionHandler;
import wamddu.backend.payment.dto.request.ConfirmPaymentRequest;
import wamddu.backend.payment.dto.response.PaymentResponse;
import wamddu.backend.payment.service.PaymentService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        userDetails = new User("1", "password", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("결제 승인 API - 성공 응답 포맷 (ApiResponse) 검증")
    void confirm_Success_ReturnsApiResponse() throws Exception {
        // given
        PaymentResponse mockResponse = new PaymentResponse(
                "ORD-20260907-001",
                "payment_key_123",
                300000L,
                "카드",
                "DONE",
                LocalDateTime.now(),
                "http://example.com/receipt"
        );

        given(paymentService.confirm(eq(1L), any(ConfirmPaymentRequest.class)))
                .willReturn(mockResponse);

        String requestJson = """
                {
                    "paymentKey": "payment_key_123",
                    "orderId": "ORD-20260907-001",
                    "amount": 300000
                }
                """;

        // when & then
        mockMvc.perform(post("/api/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("결제가 승인되었습니다."))
                .andExpect(jsonPath("$.data.orderId").value("ORD-20260907-001"))
                .andExpect(jsonPath("$.data.paymentKey").value("payment_key_123"))
                .andExpect(jsonPath("$.data.amount").value(300000))
                .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    @Test
    @DisplayName("결제 승인 API - 금액 불일치 예외 발생 시 ErrorResponse 포맷 검증")
    void confirm_AmountMismatch_ReturnsErrorResponse() throws Exception {
        // given
        given(paymentService.confirm(eq(1L), any(ConfirmPaymentRequest.class)))
                .willThrow(new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH", "결제 금액이 주문 금액과 일치하지 않습니다."));

        String requestJson = """
                {
                    "paymentKey": "payment_key_123",
                    "orderId": "ORD-20260907-001",
                    "amount": 100000
                }
                """;

        // when & then
        mockMvc.perform(post("/api/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT_MISMATCH"))
                .andExpect(jsonPath("$.message").value("결제 금액이 주문 금액과 일치하지 않습니다."));
    }
}
