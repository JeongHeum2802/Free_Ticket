package wamddu.backend.ticket.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import wamddu.backend.global.response.ApiResponse;
import wamddu.backend.ticket.dto.response.PublicTicketPriceHistoryResponse;
import wamddu.backend.ticket.service.PublicTicketPriceHistoryService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events/{eventId}/tickets/{ticketId}/price-history")
public class PublicTicketPriceHistoryController {
    private final PublicTicketPriceHistoryService service;

    @GetMapping
    public ApiResponse<PublicTicketPriceHistoryResponse> getHistory(
            @PathVariable("eventId") Long eventId, @PathVariable("ticketId") Long ticketId) {
        return ApiResponse.success("티켓 가격 이력을 조회했습니다.", service.getHistory(eventId, ticketId));
    }
}
