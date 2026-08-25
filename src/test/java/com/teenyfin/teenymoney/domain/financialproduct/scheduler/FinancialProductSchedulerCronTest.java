package com.teenyfin.teenymoney.domain.financialproduct.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialProductSchedulerCronTest {

    @Test
    @DisplayName("금융상품 스케줄러는 지정된 운영 시간에 실행된다")
    void schedulerCronDefaultsAreConfigured() throws Exception {
        assertCron(FreeSavingMonthlyScoreScheduler.class,
                "processPreviousDueDate",
                "${financial-product.free-saving-score.cron:0 0 1 * * *}");
        assertCron(FinancialProductSyncScheduler.class,
                "sync", "${finlife.sync.cron:0 0 3 * * *}");
        assertCron(FinancialProductMaturityScheduler.class,
                "processMaturities", "${financial-product.maturity.cron:0 0 12 * * *}");
        assertCron(LoanRepaymentScheduler.class,
                "processLoanRepayments",
                "${financial-product.loan-repayment.cron:0 0 14 * * *}");
        assertCron(SavingAutoPaymentScheduler.class,
                "processSavingPayments",
                "${financial-product.saving-payment.cron:0 0 16 * * *}");
    }

    private void assertCron(
            Class<?> schedulerType, String methodName, String expected) throws Exception {
        Method method = schedulerType.getMethod(methodName);
        assertEquals(expected, method.getAnnotation(Scheduled.class).cron());
    }
}
