package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class LoanRepaymentHistoryVO {
    private Integer installmentNo;
    private String repaymentType;
    private Long principalAmount;
    private Long paidPrincipalAmount;
    private Long interestAmount;
    private Long paidInterestAmount;
    private String status;
    private LocalDateTime overdueStartAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
