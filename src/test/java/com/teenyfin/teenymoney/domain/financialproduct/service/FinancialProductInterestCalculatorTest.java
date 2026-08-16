package com.teenyfin.teenymoney.domain.financialproduct.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialProductInterestCalculatorTest {
    private final FinancialProductInterestCalculator calculator =
            new FinancialProductInterestCalculator();

    @Test
    @DisplayName("단리 이자는 실제 예치 일수로 계산하고 원 단위 미만을 버린다")
    void simpleInterestUsesActualDays() {
        long interest = calculator.calculate(100_000L, new BigDecimal("3.65"),
                "SIMPLE", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1));

        assertEquals(3_650L, interest);
    }

    @Test
    @DisplayName("복리 이자는 실제 예치 일수를 사용하는 일복리로 계산한다")
    void compoundInterestCompoundsDaily() {
        long simple = calculator.calculate(1_000_000L, new BigDecimal("10.00"),
                "SIMPLE", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1));
        long compound = calculator.calculate(1_000_000L, new BigDecimal("10.00"),
                "COMPOUND", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1));

        assertTrue(compound > simple);
    }
}
