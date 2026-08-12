package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class FinancialProductApprovalVO {
    private Long enrollmentId;
    private Long productId;
    private FinancialProductType productType;
    private String productName;
    private Long parentId;
    private Long childId;
    private String childName;
    private Long walletId;
    private Long transferId;
    private Long requestedAmount;
    private Integer termMonths;
    private Integer paymentDay;
    private Boolean autoTransfer;
    private String savingsType;
    private String interestCalculationType;
    private String repaymentType;
    private BigDecimal appliedRate;
    private BigDecimal earlyTerminationRate;
    private BigDecimal lateFeeRate;
    private String status;
    private LocalDateTime requestedAt;
}
