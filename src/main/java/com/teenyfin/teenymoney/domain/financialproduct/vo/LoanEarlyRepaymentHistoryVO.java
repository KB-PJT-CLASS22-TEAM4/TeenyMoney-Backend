package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 조기상환 멱등키 재요청 시 최초 처리 결과를 복원하는 데 필요한 값만 담는다. */
@Getter
@Setter
@NoArgsConstructor
public class LoanEarlyRepaymentHistoryVO {
    private Long transferId;
    private Long paidPrincipalAmount;
    private Long paidInterestAmount;
}
