package com.teenyfin.teenymoney.domain.financialproduct.scheduler;

import com.teenyfin.teenymoney.domain.financialproduct.service.SavingAutoPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Component
public class SavingAutoPaymentScheduler {
    private final SavingAutoPaymentService savingAutoPaymentService;
    private final Clock clock;

    public SavingAutoPaymentScheduler(
            SavingAutoPaymentService savingAutoPaymentService,
            Clock clock) {
        this.savingAutoPaymentService = savingAutoPaymentService;
        this.clock = clock;
    }

    @Scheduled(cron = "${financial-product.saving-payment.cron:0 0 16 * * *}",
            zone = "Asia/Seoul")
    public void processSavingPayments() {
        LocalDate paymentDate = LocalDate.now(clock);
        try {
            int count = savingAutoPaymentService.processDuePayments(paymentDate);
            log.info("적금 자동납입 처리 완료: date={}, count={}", paymentDate, count);
        } catch (RuntimeException exception) {
            log.error("적금 자동납입 처리 실패: date={}", paymentDate, exception);
        }
    }
}
