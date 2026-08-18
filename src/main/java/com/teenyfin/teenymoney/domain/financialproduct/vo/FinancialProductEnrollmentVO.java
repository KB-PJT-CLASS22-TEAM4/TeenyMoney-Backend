package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class FinancialProductEnrollmentVO {
    private Long enrollmentId;
    private Long productId;
    private FinancialProductType productType;
    private String financialCompanyName;
    private String productName;
    private String description;
    private String savingsType;
    private String interestCalculationType;
    private String status;
    private BigDecimal appliedRate;
    private BigDecimal appliedEarlyTerminationRate;
    private BigDecimal appliedLateFeeRate;
    private Integer termMonths;
    private LocalDate startDate;
    private LocalDate maturityDate;
    private LocalDateTime closedAt;
    private LocalDateTime requestedAt;
    private Long depositAmount;
    private Long monthlyAmount;
    private Long accumulatedAmount;
    private Integer paidCount;
    private Integer totalPaymentCount;
    private Integer paymentDay;
    private LocalDate nextPaymentDate;
    private Boolean autoTransfer;
    private Long principalAmount;
    private Long outstandingPrincipal;
    private Long overdueInterest;
    private String repaymentType;
}
