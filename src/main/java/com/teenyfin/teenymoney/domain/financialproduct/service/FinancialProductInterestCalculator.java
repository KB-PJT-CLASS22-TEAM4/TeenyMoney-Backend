package com.teenyfin.teenymoney.domain.financialproduct.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class FinancialProductInterestCalculator {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365);

    public long calculate(long principal, BigDecimal annualRate,
                          String calculationType, LocalDate from, LocalDate to) {
        if (principal <= 0 || annualRate == null || from == null || to == null) {
            return 0L;
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(from, to));
        if (days == 0) return 0L;

        BigDecimal rate = annualRate.divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP);
        BigDecimal interest;
        if ("COMPOUND".equals(calculationType)) {
            // 이 프로젝트는 중도해지의 부분 월 처리까지 동일하게 적용하기 위해 365일 일복리를 사용한다.
            BigDecimal dailyFactor = BigDecimal.ONE.add(
                    rate.divide(DAYS_PER_YEAR, 16, RoundingMode.HALF_UP));
            interest = BigDecimal.valueOf(principal)
                    .multiply(dailyFactor.pow(Math.toIntExact(days)))
                    .subtract(BigDecimal.valueOf(principal));
        } else {
            interest = BigDecimal.valueOf(principal)
                    .multiply(rate)
                    .multiply(BigDecimal.valueOf(days))
                    .divide(DAYS_PER_YEAR, 12, RoundingMode.DOWN);
        }
        return interest.setScale(0, RoundingMode.DOWN).longValueExact();
    }
}
