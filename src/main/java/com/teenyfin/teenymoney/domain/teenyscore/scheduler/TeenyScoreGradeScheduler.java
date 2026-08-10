package com.teenyfin.teenymoney.domain.teenyscore.scheduler;

import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreGradeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매월 1일 00:00(한국 시간)에 직전 활동까지 반영된 점수로 새 등급을 확정한다. */
@Component
public class TeenyScoreGradeScheduler {

    private final TeenyScoreGradeService teenyScoreGradeService;

    public TeenyScoreGradeScheduler(
            TeenyScoreGradeService teenyScoreGradeService) {
        this.teenyScoreGradeService = teenyScoreGradeService;
    }

    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")
    public void updateMonthlyGrades() {
        teenyScoreGradeService.applyMonthlyGrades();
    }
}
