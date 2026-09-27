package wamddu.backend.payment.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.reconciliation.enabled", havingValue = "true")
public class PaymentReconciliationScheduler {
    private final PaymentReconciliationService service;

    @Scheduled(fixedDelayString = "${payment.reconciliation.delay-ms:3600000}",
            initialDelayString = "${payment.reconciliation.initial-delay-ms:60000}")
    public void reconcile() {
        try {
            var summary = service.reconcileUntil(Instant.now());
            log.info("Payment reconciliation finished: windows={}, transactions={}, newIssues={}, completedThrough={}",
                    summary.windows(), summary.transactions(), summary.newIssues(), summary.completedThrough());
        } catch (RuntimeException failure) {
            log.error("Payment reconciliation failed; progress remains at last completed window", failure);
        }
    }
}
