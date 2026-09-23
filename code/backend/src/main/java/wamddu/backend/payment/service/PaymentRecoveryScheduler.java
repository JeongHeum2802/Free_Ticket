package wamddu.backend.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "payment.recovery.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {
    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${payment.recovery.delay-ms:30000}",
            initialDelayString = "${payment.recovery.delay-ms:30000}")
    public void recover() {
        try {
            paymentService.recoverPendingPayments();
        } catch (RuntimeException failure) {
            log.error("결제 복구 대상 조회 실패. 다음 주기에 다시 확인합니다.", failure);
        }
    }
}
