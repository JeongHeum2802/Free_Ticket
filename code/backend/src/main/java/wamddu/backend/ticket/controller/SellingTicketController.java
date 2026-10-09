package wamddu.backend.ticket.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import wamddu.backend.global.response.ApiResponse;
import wamddu.backend.ticket.dto.response.SellingTicketResponse;
import wamddu.backend.ticket.dto.response.TicketPriceHistoryResponse;
import wamddu.backend.ticket.service.SellingTicketService;
import wamddu.backend.ticket.service.TicketPricingService;
import wamddu.backend.ticket.dto.request.TicketPriceSettingsRequest;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class SellingTicketController {
    private final SellingTicketService service;
    private final TicketPricingService pricingService;

    @PutMapping("/{ticketId}/price-settings")
    public ApiResponse<SellingTicketResponse> configurePrice(@AuthenticationPrincipal UserDetails principal,
            @PathVariable Long ticketId, @Valid @RequestBody TicketPriceSettingsRequest request) {
        return ApiResponse.success("가격 설정을 저장했습니다.",
                pricingService.configure(Long.parseLong(principal.getUsername()), ticketId, request));
    }

    @GetMapping("/me/selling")
    public ApiResponse<List<SellingTicketResponse>> getMyTickets(@AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.success("판매 티켓을 조회했습니다.", service.getMyTickets(Long.parseLong(principal.getUsername())));
    }

    @GetMapping("/{ticketId}/price-history")
    public ApiResponse<TicketPriceHistoryResponse> getHistory(@AuthenticationPrincipal UserDetails principal,
                                                            @PathVariable Long ticketId) {
        return ApiResponse.success("가격 이력을 조회했습니다.",
                service.getHistory(Long.parseLong(principal.getUsername()), ticketId));
    }
}
