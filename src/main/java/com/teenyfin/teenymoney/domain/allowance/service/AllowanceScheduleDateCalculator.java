package com.teenyfin.teenymoney.domain.allowance.service;


import java.time.DayOfWeek;
import java.time.LocalDate;

// WEEKLY/MONTHLY 공통 "다음 지급일" 계산. referenceDate 당일은 항상 제외하고, 항상 미래
// 시점의 다음 발생일을 돌려준다 - 오늘이 마침 그 요일/그 날짜여도 즉시 나가지 않는다.
// 생성/수정/재활성화 시(referenceDate=오늘)와, 배치가 지급을 마친 뒤 다음 회차로 넘어갈 때
// (referenceDate=방금 처리한 next_payment_date) 양쪽에서 이 메서드 하나를 재사용한다.
public class AllowanceScheduleDateCalculator {

    // 정적 메서드만 있는 유틸리티 클래스라 인스턴스화를 막아둠 (실수로 new AllowanceScheduleDateCalculator() 못 하게)
    private AllowanceScheduleDateCalculator() {

    }

    public static LocalDate calculateNext(LocalDate referenceDate, String cycleType, int paymentDay) {
        if ("WEEKLY".equals(cycleType)) {
            return nextWeekly(referenceDate, paymentDay);
        }
        if ("MONTHLY".equals(cycleType)) {
            return nextMonthly(referenceDate, paymentDay);
        }
        throw new IllegalArgumentException("알 수 없는 cycleType: " + cycleType);
    }

    // paymentDay: ISO-8601 기준 1=월요일~7=일요일 (DayOfWeek.getValue()와 동일).
    // referenceDate 다음날부터 하루씩 밀어보면서 목표 요일이 나올 때까지 찾는다.
    // 최악의 경우(오늘이 목표 요일 바로 다음날)에도 7번이면 끝나므로 반복 횟수는 문제없음.
    private static LocalDate nextWeekly(LocalDate referenceDate, int paymentDay) {
        DayOfWeek target = DayOfWeek.of(paymentDay);
        LocalDate candidate = referenceDate.plusDays(1);
        while (candidate.getDayOfWeek() != target) {
            candidate = candidate.plusDays(1);
        }
        return candidate;

    }

    // paymentDay: 1~28 (DB CHECK로 이미 29~31 불가). referenceDate가 속한 달과 상관없이
    // 무조건 "다음 달"로 넘어가므로 "그 달에 없는 날짜" 문제(예: 2월 30일)가 애초에 안 생긴다.
    private static LocalDate nextMonthly(LocalDate referenceDate, int paymentDay) {
        return referenceDate.plusMonths(1).withDayOfMonth(paymentDay);
    }
}
