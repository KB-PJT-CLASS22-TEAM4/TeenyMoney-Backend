package com.teenyfin.teenymoney.domain.financialproduct.scheduler;

import com.teenyfin.teenymoney.domain.financialproduct.service.LoanRepaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** 매일 도래한 대출 회차를 처리하며, cron 속성으로 로컬 테스트 주기를 변경할 수 있다. */
@Slf4j
@Component
public class LoanRepaymentScheduler {
    private final LoanRepaymentService service;
    private final Clock clock;

    public LoanRepaymentScheduler(LoanRepaymentService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "${financial-product.loan-repayment.cron:0 0 14 * * *}",
            zone = "Asia/Seoul")
    public void processLoanRepayments() {
        LocalDate date = LocalDate.now(clock);
        try {
            log.info("대출 자동상환 처리 완료: date={}, count={}",
                    date, service.processDueRepayments(date));
        } catch (RuntimeException exception) {
            log.error("대출 자동상환 배치 실패: date={}", date, exception);
        }
    }
}
