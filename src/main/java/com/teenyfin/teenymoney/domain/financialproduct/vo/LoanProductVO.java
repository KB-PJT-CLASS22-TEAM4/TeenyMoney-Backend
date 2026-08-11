package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class LoanProductVO {
    private Long id;
    private String name;
    private BigDecimal baseRate;
    private BigDecimal lateFeeRate;
    private String repaymentType;
    private Long minAmount;
    private Long maxAmount;
    private Long requiredGradeId;
    private String requiredGradeName;
    private String description;
    private Boolean active;
}
