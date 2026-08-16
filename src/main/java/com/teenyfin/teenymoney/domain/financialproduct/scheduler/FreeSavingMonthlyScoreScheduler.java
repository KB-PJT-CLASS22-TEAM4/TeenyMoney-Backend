package com.teenyfin.teenymoney.domain.financialproduct.scheduler;

import com.teenyfin.teenymoney.domain.financialproduct.service.FreeSavingMonthlyScoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;

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

    @Scheduled(cron = "${financial-product.free-saving-score.cron:0 1 0 1 * *}",
            zone = "Asia/Seoul")
    public void processPreviousMonth() {
        YearMonth targetMonth = YearMonth.now(clock).minusMonths(1);
        int count = scoreService.processMonthlyScores(targetMonth);
        log.info("자유적금 월별 점수 처리 완료: month={}, count={}", targetMonth, count);
    }
}
