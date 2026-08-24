package com.teenyfin.teenymoney.domain.financialproduct.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 자유적금의 지정 납입일 기준 회차 구간이다. 종료 시각은 포함하지 않는다. */
public record FreeSavingCycle(
        int installmentNo,
        LocalDate dueDate,
        LocalDateTime startInclusive,
        LocalDateTime endExclusive) {
}
