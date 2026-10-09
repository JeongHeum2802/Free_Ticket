package wamddu.backend.ticket.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import wamddu.backend.ticket.repository.TicketRepository;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ticket.pricing.enabled", havingValue = "true", matchIfMissing = true)
public class TicketPricingScheduler {
    private final TicketRepository tickets;
    private final TicketPricingService service;

    // Poll for due tickets; each ticket's persisted evaluation time enforces the two-hour interval.
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void adjustPrices() {
        var now = LocalDateTime.now();
        try {
            for (Long id : tickets.findPriceAdjustmentCandidates(now, now.minus(TicketPricingService.OBSERVATION_PERIOD))) {
                try {
                    service.adjustPrice(id, now);
                } catch (RuntimeException failure) {
                    log.error("티켓 가격 조정 실패. ticketId={}", id, failure);
                }
            }
        } catch (RuntimeException failure) {
            log.error("가격 조정 대상 조회 실패. 다음 주기에 다시 확인합니다.", failure);
        }
    }
}
