package com.teenyfin.teenymoney.domain.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 이체 기준 돈의 이동. 예금은 납입 이력 테이블이 없어 이체가 유일한 소스다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoneyFlowVO {

    private long depositAmount;
    private long savingAmount;
    private long earnedAmount;
    private int questRewardCount;
    private int savingProductCount;
    private int savingPaymentCount;
    private int depositProductCount;
    private int depositPaymentCount;
}
