package com.teenyfin.teenymoney.domain.financialproduct.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FreeSavingCycleCalculatorTest {
    private final FreeSavingCycleCalculator calculator =
            new FreeSavingCycleCalculator();

    @Test
    @DisplayName("최초 회차는 계약 시작일부터 최초 지정 납입일까지다")
    void calculatesFirstCycle() {
        FreeSavingCycle cycle = calculator.forDueDate(
                LocalDate.of(2026, 8, 10), 20,
                LocalDate.of(2026, 8, 20));

        assertEquals(1, cycle.installmentNo());
        assertEquals(LocalDate.of(2026, 8, 10).atStartOfDay(),
                cycle.startInclusive());
        assertEquals(LocalDate.of(2026, 8, 21).atStartOfDay(),
                cycle.endExclusive());
    }

    @Test
    @DisplayName("지정일 다음 날 납입은 다음 회차로 계산한다")
    void paymentAfterDueDateBelongsToNextCycle() {
        FreeSavingCycle cycle = calculator.forPayment(
                LocalDate.of(2026, 8, 10), 20,
                LocalDate.of(2026, 8, 21));

        assertEquals(2, cycle.installmentNo());
        assertEquals(LocalDate.of(2026, 8, 21).atStartOfDay(),
                cycle.startInclusive());
        assertEquals(LocalDate.of(2026, 9, 21).atStartOfDay(),
                cycle.endExclusive());
    }
}
