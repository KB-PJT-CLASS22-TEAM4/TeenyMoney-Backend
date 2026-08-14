package com.teenyfin.teenymoney.domain.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 한 기간의 성공 결제 총액과 건수. 조회 기간과 비교 기간에 같은 모양으로 쓴다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpendingTotalVO {

    private long totalAmount;
    private int paymentCount;
}
