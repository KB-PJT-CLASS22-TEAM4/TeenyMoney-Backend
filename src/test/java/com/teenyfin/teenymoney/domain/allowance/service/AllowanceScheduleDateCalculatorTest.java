package com.teenyfin.teenymoney.domain.allowance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AllowanceScheduleDateCalculatorTest {

    // 화요일에 "다음 월요일"을 계산하면 그 주 남은 날짜만큼 정확히 미래로 가는지
    @Test
    @DisplayName("WEEKLY: 오늘이 화요일이고 payment_day=월요일이면 6일 뒤(다음 주 월요일)")
    void weeklyFromTuesdayToMonday() {
        LocalDate tuesday = LocalDate.of(2026, 8, 11); // 화요일
        LocalDate result = AllowanceScheduleDateCalculator.calculateNext(tuesday, "WEEKLY", 1);
        assertEquals(LocalDate.of(2026, 8, 17), result); // 다음 월요일
    }

    // 오늘이 마침 목표 요일(월요일)이어도 오늘이 아니라 다음 주 같은 요일이 나오는지 (오늘 당일 배제 규칙)
    @Test
    @DisplayName("WEEKLY: 오늘이 마침 그 요일(월요일)이어도 오늘이 아니라 다음 주 같은 요일")
    void weeklySkipsTodayEvenIfItMatches() {
        LocalDate monday = LocalDate.of(2026, 8, 10); // 월요일
        LocalDate result = AllowanceScheduleDateCalculator.calculateNext(monday, "WEEKLY", 1);
        assertEquals(LocalDate.of(2026, 8, 17), result); // 오늘(8/10) 아니고 다음 주 월요일
    }

    // (1번 버그 수정 검증) 이번 달 payDay가 아직 안 지났으면 다음 달로 안 넘기고 이번 달 날짜를 그대로 쓰는지
    @Test
    @DisplayName("MONTHLY: 오늘이 3/10이고 payment_day=15면 아직 안 지났으니 이번 달 15일")
    void monthlyUsesThisMonthDateWhenNotYetPassed() {
        LocalDate march10 = LocalDate.of(2026, 3, 10);
        LocalDate result = AllowanceScheduleDateCalculator.calculateNext(march10, "MONTHLY", 15);
        assertEquals(LocalDate.of(2026, 3, 15), result);
    }

    // 이번 달 payDay가 이미 지났으면 예상대로 다음 달로 넘어가는지
    @Test
    @DisplayName("MONTHLY: 오늘이 3/20이고 payment_day=15면 이미 지났으니 다음 달 15일")
    void monthlyJumpsToNextMonthWhenPaymentDayAlreadyPassed() {
        LocalDate march20 = LocalDate.of(2026, 3, 20);
        LocalDate result = AllowanceScheduleDateCalculator.calculateNext(march20, "MONTHLY", 15);
        assertEquals(LocalDate.of(2026, 4, 15), result);
    }

    // 오늘이 마침 payDay와 같은 날이어도 오늘이 아니라 다음 달로 넘어가는지 (WEEKLY와 같은 "오늘 당일 배제" 원칙)
    @Test
    @DisplayName("MONTHLY: 오늘이 마침 그 날짜(3/15)여도 오늘이 아니라 다음 달 15일")
    void monthlySkipsTodayEvenIfItMatches() {
        LocalDate march15 = LocalDate.of(2026, 3, 15);
        LocalDate result = AllowanceScheduleDateCalculator.calculateNext(march15, "MONTHLY", 15);
        assertEquals(LocalDate.of(2026, 4, 15), result);
    }

    // 12월에 이미 지난 날짜를 계산하면 다음 달 계산이 연도까지 자연스럽게 넘어가는지
    @Test
    @DisplayName("MONTHLY: 12월에 이미 지난 날짜로 계산하면 다음 달(1월)로 연도가 넘어간다")
    void monthlyRollsOverIntoNextYearWhenAlreadyPassed() {
        LocalDate december20 = LocalDate.of(2026, 12, 20);
        LocalDate result = AllowanceScheduleDateCalculator.calculateNext(december20, "MONTHLY", 5);
        assertEquals(LocalDate.of(2027, 1, 5), result);
    }
}
