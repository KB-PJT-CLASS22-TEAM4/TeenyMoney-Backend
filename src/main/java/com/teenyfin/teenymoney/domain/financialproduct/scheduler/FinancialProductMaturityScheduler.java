package com.teenyfin.teenymoney.domain.financialproduct.scheduler;

import com.teenyfin.teenymoney.domain.financialproduct.service.FinancialProductMaturityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Component
public class FinancialProductMaturityScheduler {
    private final FinancialProductMaturityService maturityService;
    private final Clock clock;

    public FinancialProductMaturityScheduler(
            FinancialProductMaturityService maturityService, Clock clock) {
        this.maturityService = maturityService;
        this.clock = clock;
    }

    @Scheduled(cron = "${financial-product.maturity.cron:0 10 0 * * *}",
            zone = "Asia/Seoul")
    public void processMaturities() {
        LocalDate date = LocalDate.now(clock);
        int count = maturityService.processMaturities(date);
        log.info("예·적금 만기 정산 완료: date={}, count={}", date, count);
    }
}
