package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class FinancialProductEnrollmentCommandVO {
    private Long id;
    private Long productId;
    private Long parentId;
    private Long childId;
    private Long walletId;
    private BigDecimal appliedRate;
    private BigDecimal appliedEarlyTerminationRate;
    private BigDecimal appliedLateFeeRate;
    private Long amount;
    private Integer termMonths;
    private Integer paymentDay;
    private Boolean autoTransfer;
}
