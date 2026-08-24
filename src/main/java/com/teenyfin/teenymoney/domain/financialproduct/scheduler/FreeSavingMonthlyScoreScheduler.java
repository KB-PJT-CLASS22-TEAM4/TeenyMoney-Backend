package com.teenyfin.teenymoney.domain.financialproduct.scheduler;

import com.teenyfin.teenymoney.domain.financialproduct.service.FreeSavingMonthlyScoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Component
public class FreeSavingMonthlyScoreScheduler {
    private final FreeSavingMonthlyScoreService scoreService;
    private final Clock clock;

    public FreeSavingMonthlyScoreScheduler(
            FreeSavingMonthlyScoreService scoreService, Clock clock) {
        this.scoreService = scoreService;
        this.clock = clock;
    }

    @Scheduled(cron = "${financial-product.free-saving-score.cron:0 0 1 * * *}",
            zone = "Asia/Seoul")
    public void processPreviousDueDate() {
        LocalDate dueDate = LocalDate.now(clock).minusDays(1);
        int count = scoreService.processDueDate(dueDate);
        log.info("자유적금 회차 확정 완료: dueDate={}, count={}", dueDate, count);
    }
}
