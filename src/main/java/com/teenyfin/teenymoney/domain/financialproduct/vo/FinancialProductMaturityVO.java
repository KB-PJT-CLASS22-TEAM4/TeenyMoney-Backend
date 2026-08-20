package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class FinancialProductMaturityVO {
    private Long enrollmentId;
    private Long childId;
    private Long parentId;
    private Long productWalletId;
    private String productName;
    private BigDecimal appliedRate;
    private Integer termMonths;
    private Long monthlyAmount;
    private String savingsType;
    private String interestCalculationType;
    private LocalDate startDate;
    private LocalDate maturityDate;
    private String status;
}
