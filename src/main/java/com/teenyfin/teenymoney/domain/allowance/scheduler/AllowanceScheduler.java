package com.teenyfin.teenymoney.domain.allowance.scheduler;


import com.teenyfin.teenymoney.domain.allowance.service.AllowanceScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

// 매일 한 번 오늘 날짜로 배치를 돌린다.
@Slf4j
@Component
public class AllowanceScheduler {
    private final AllowanceScheduleService allowanceScheduleService;
    private final Clock clock;

    public AllowanceScheduler(AllowanceScheduleService allowanceScheduleService, Clock clock) {
        this.allowanceScheduleService = allowanceScheduleService;
        this.clock = clock;
    }

    @Scheduled(cron = "${allowance.schedule.cron:0 10 0 * * *}", zone = "Asia/Seoul")
    public void processDuePayments() {
        LocalDate paymentDate = LocalDate.now(clock);

        try{
            int count = allowanceScheduleService.processDuePayments(paymentDate);
            log.info("정기 용돈 배치 처리 완료: date={}, count={}", paymentDate, count);
        } catch(RuntimeException exception) {
            log.error("정기 용돈 배치 처리 실패: date={} ", paymentDate, exception);
        }

    }
}
