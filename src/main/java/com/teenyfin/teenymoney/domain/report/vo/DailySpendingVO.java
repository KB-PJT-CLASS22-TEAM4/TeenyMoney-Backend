package com.teenyfin.teenymoney.domain.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 일자별 성공 결제 합계.
 *
 * 주차 버킷팅은 SQL이 아니라 ReportPeriodCalculator가 한다. 한 달이면 최대 31행이라
 * 비용이 없고, 주차 규칙(월~일, 첫·마지막 주 절단)을 DB 없이 테스트할 수 있다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySpendingVO {

    private LocalDate spentDate;
    private long amount;
    private int paymentCount;
}
