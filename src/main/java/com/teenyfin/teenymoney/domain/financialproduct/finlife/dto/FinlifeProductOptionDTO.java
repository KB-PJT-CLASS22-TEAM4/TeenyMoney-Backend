package com.teenyfin.teenymoney.domain.financialproduct.finlife.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinlifeProductOptionDTO {
    @JsonProperty("fin_co_no")
    private String financialCompanyCode;
    @JsonProperty("fin_prdt_cd")
    private String financialProductCode;
    @JsonProperty("save_trm")
    private String savingTerm;
    @JsonProperty("intr_rate")
    private BigDecimal interestRate;
    @JsonProperty("intr_rate_type")
    private String interestRateType;
    @JsonProperty("rsrv_type")
    private String reserveType;
}
