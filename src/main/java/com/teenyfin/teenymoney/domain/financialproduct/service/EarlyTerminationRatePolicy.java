package com.teenyfin.teenymoney.domain.financialproduct.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 예·적금 중도해지 진행률에 따라 가입 당시 기준금리에 정책 비율을 적용한다. */
@Component
public class EarlyTerminationRatePolicy {

    // 정책 엑셀에 정의된 가입기간 진행률별 중도해지 금리 적용 비율이다.
    private static final BigDecimal TEN_PERCENT = new BigDecimal("0.10");
    private static final BigDecimal THIRTY_PERCENT = new BigDecimal("0.30");
    private static final BigDecimal SIXTY_PERCENT = new BigDecimal("0.60");
    private static final BigDecimal EIGHTY_PERCENT = new BigDecimal("0.80");

    /**
     * @param baseRate 상품 가입 시 저장한 중도해지 기준금리(%)
     * @param progressPercent 가입기간 진행률. 중도해지이므로 0 이상 100 미만이다.
     * @return 진행률별 정책 비율이 적용된 중도해지 금리(%)
     */
    public BigDecimal calculate(BigDecimal baseRate, int progressPercent) {
        if (baseRate == null || baseRate.signum() <= 0) {
            throw new IllegalArgumentException("중도해지 기준금리는 0보다 커야 합니다.");
        }
        if (progressPercent < 0 || progressPercent >= 100) {
            throw new IllegalArgumentException("중도해지 진행률은 0 이상 100 미만이어야 합니다.");
        }

        // 상품 기본금리가 아니라 가입 시 저장한 중도해지 기준금리에 비율을 곱한다.
        return baseRate.multiply(multiplier(progressPercent));
    }

    private BigDecimal multiplier(int progressPercent) {
        if (progressPercent < 25) {
            return TEN_PERCENT; // 진행률 0~24%: 기준금리의 10%
        }
        if (progressPercent < 50) {
            return THIRTY_PERCENT; // 진행률 25~49%: 기준금리의 30%
        }
        if (progressPercent < 75) {
            return SIXTY_PERCENT; // 진행률 50~74%: 기준금리의 60%
        }
        return EIGHTY_PERCENT; // 진행률 75~99%: 기준금리의 80%
    }
}
