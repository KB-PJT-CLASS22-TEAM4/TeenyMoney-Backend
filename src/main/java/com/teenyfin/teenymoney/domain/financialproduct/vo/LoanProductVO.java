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
    // 상품 출처와 부모 상품의 생성자·대상 자녀 범위
    private FinancialProductSource productSource;
    private Long createdByParentId;
    private Long targetChildId;
    private String name;
    private BigDecimal baseRate;
    private Boolean available1m;
    private Boolean available3m;
    private Boolean available6m;
    private Boolean available12m;
    private BigDecimal lateFeeRate;
    private String repaymentType;
    private Long minAmount;
    private Long maxAmount;
    private Long requiredGradeId;
    private String requiredGradeName;
    private String description;
    private Boolean active;
}
