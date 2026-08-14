package com.teenyfin.teenymoney.domain.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 대출 상환 이력의 실제 납부액. 정의서 6.2가 원금과 이자를 나눠 요구한다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanRepaymentVO {

    private long paidPrincipal;
    private long paidInterest;
    private int repaidCount;
}
