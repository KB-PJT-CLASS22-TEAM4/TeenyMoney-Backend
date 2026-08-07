package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class FinancialProductBenefitVO {
    private Long childId;
    private Integer teenyScore;
    private Long gradeId;
    private String gradeName;
    private BigDecimal bonusRate;
    private BigDecimal loanRate;
}
