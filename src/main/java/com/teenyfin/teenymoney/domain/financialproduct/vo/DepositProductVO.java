package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class DepositProductVO {
    private Long id;
    private String financialCompanyCode;
    private String financialProductCode;
    private String financialCompanyName;
    private String name;
    private String interestCalculationType;
    private BigDecimal rate1m;
    private BigDecimal rate3m;
    private BigDecimal rate6m;
    private BigDecimal rate12m;
    private BigDecimal earlyTerminationRate;
    private Long minAmount;
    private Long maxAmount;
    private String description;
    private Boolean active;
}
