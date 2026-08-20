package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 자동상환 계산과 상태 갱신에 필요한 대출 계약 스냅샷이다. */
@Getter
@Setter
@NoArgsConstructor
public class LoanRepaymentVO {
    private Long enrollmentId;
    private Long parentId;
    private Long childId;
    private String productName;
    private Long principalAmount;
    private Long outstandingPrincipal;
    private Long overdueInterest;
    private BigDecimal appliedRate;
    private BigDecimal appliedLateFeeRate;
    private Integer paymentDay;
    private Integer paidCount;
    private Integer termMonths;
    private LocalDate startDate;
    private LocalDate maturityDate;
    private String repaymentType;
    private String status;
}
