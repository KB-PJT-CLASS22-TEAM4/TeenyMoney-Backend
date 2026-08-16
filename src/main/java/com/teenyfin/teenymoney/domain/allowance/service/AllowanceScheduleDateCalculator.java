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

    // referenceDate가 속한 "이번 달"에 paymentDay가 아직 안 지났으면 이번 달 걸 쓰고,
    // 이미 지났거나(오늘보다 과거) 오늘이 마침 그 날짜면(오늘 당일은 항상 제외) 다음 달로 넘긴다.
    private static LocalDate nextMonthly(LocalDate referenceDate, int paymentDay) {
        // referenceDate와 같은 달, 일자만 paymentDay로 바꾼 날짜를 먼저 만들어본다.
        // (paymentDay가 1~28로 제한돼 있어서 어떤 달이든 이 날짜는 항상 유효함 - 2월도 28일까지는 있음)
        LocalDate thisMonth = referenceDate.withDayOfMonth(paymentDay);

        // isAfter()는 "진짜로 미래인지"만 true를 준다 - 같은 날짜(오늘=paymentDay인 경우)는 false.
        // 그래서 이 조건은 "이번 달 paymentDay가 오늘보다 뒤에 있는 경우"에만 통과한다.
        if (thisMonth.isAfter(referenceDate)) {
            return thisMonth;
        }

        // 여기로 떨어지는 경우: 이번 달 paymentDay가 이미 지났거나(예: 오늘 8/20, payDay=15),
        // 오늘이 마침 그 날짜라서(예: 오늘 8/15, payDay=15) 제외해야 하는 경우.
        // 두 경우 다 다음 달로 넘긴다 - 기존 로직과 동일.
        return referenceDate.plusMonths(1).withDayOfMonth(paymentDay);
    }
}
