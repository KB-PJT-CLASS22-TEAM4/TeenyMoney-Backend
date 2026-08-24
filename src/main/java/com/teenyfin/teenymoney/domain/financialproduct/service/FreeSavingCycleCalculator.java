package com.teenyfin.teenymoney.domain.financialproduct.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** 계약 시작일과 지정 납입일로 자유적금 회차와 인정 기간을 계산한다. */
@Component
public class FreeSavingCycleCalculator {

    public FreeSavingCycle forPayment(
            LocalDate startDate, int paymentDay, LocalDate paymentDate) {
        // 지정일 다음 날부터는 다음 회차 납입으로 계산한다.
        LocalDate firstDueDate = firstDueDate(startDate, paymentDay);
        LocalDate dueDate;
        if (!paymentDate.isAfter(firstDueDate)) {
            dueDate = firstDueDate;
        } else {
            LocalDate thisMonthDue = paymentDate.withDayOfMonth(paymentDay);
            dueDate = paymentDate.isAfter(thisMonthDue)
                    ? thisMonthDue.plusMonths(1) : thisMonthDue;
        }
        return cycle(startDate, firstDueDate, dueDate);
    }

    public FreeSavingCycle forDueDate(
            LocalDate startDate, int paymentDay, LocalDate dueDate) {
        LocalDate firstDueDate = firstDueDate(startDate, paymentDay);
        if (dueDate.getDayOfMonth() != paymentDay
                || dueDate.isBefore(firstDueDate)) {
            throw new IllegalArgumentException("유효하지 않은 자유적금 납입일입니다.");
        }
        return cycle(startDate, firstDueDate, dueDate);
    }

    public LocalDate firstDueDate(LocalDate startDate, int paymentDay) {
        LocalDate sameMonth = startDate.withDayOfMonth(paymentDay);
        return paymentDay > startDate.getDayOfMonth()
                ? sameMonth : sameMonth.plusMonths(1);
    }

    private FreeSavingCycle cycle(
            LocalDate startDate, LocalDate firstDueDate, LocalDate dueDate) {
        int installmentNo = Math.toIntExact(ChronoUnit.MONTHS.between(
                YearMonth.from(firstDueDate), YearMonth.from(dueDate)) + 1);
        // 최초 회차만 계약 시작일부터, 이후 회차는 이전 지정일 다음 날부터 인정한다.
        LocalDate cycleStart = installmentNo == 1
                ? startDate : dueDate.minusMonths(1).plusDays(1);
        return new FreeSavingCycle(
                installmentNo, dueDate, cycleStart.atStartOfDay(),
                dueDate.plusDays(1).atStartOfDay());
    }
}
