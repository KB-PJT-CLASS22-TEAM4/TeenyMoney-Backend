package com.teenyfin.teenymoney.domain.financialproduct.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EarlyTerminationRatePolicyTest {

    private final EarlyTerminationRatePolicy policy = new EarlyTerminationRatePolicy();

    @Test
    @DisplayName("진행률 0·25·50·75% 경계에서 기준금리의 10·30·60·80%를 적용한다")
    void appliesProgressMultiplierAtBoundaries() {
        BigDecimal baseRate = new BigDecimal("2.00");

        assertRate("0.20", policy.calculate(baseRate, 0));
        assertRate("0.20", policy.calculate(baseRate, 24));
        assertRate("0.60", policy.calculate(baseRate, 25));
        assertRate("0.60", policy.calculate(baseRate, 49));
        assertRate("1.20", policy.calculate(baseRate, 50));
        assertRate("1.20", policy.calculate(baseRate, 74));
        assertRate("1.60", policy.calculate(baseRate, 75));
        assertRate("1.60", policy.calculate(baseRate, 99));
    }

    @Test
    @DisplayName("부모 설정 기준금리 1.00%와 진행률 25%이면 중도해지 금리는 0.30%다")
    void usesParentConfiguredEarlyTerminationRateAsBase() {
        assertRate("0.30", policy.calculate(new BigDecimal("1.00"), 25));
    }

    @Test
    @DisplayName("중도해지 기준금리가 null이면 계산을 거부한다")
    void rejectsNullBaseRate() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.calculate(null, 50));
    }

    @Test
    @DisplayName("중도해지 기준금리가 0이면 계산을 거부한다")
    void rejectsZeroBaseRate() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.calculate(BigDecimal.ZERO, 50));
    }

    @Test
    @DisplayName("진행률이 0% 미만이면 중도해지 금리 계산을 거부한다")
    void rejectsNegativeProgress() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.calculate(BigDecimal.ONE, -1));
    }

    @Test
    @DisplayName("만기인 진행률 100%는 중도해지 금리 계산을 거부한다")
    void rejectsMaturityProgress() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.calculate(BigDecimal.ONE, 100));
    }

    private void assertRate(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
