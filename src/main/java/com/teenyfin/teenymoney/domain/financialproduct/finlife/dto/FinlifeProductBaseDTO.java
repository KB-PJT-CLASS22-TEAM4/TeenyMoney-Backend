package com.teenyfin.teenymoney.domain.financialproduct.finlife.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinlifeProductBaseDTO {
    @JsonProperty("fin_co_no")
    private String financialCompanyCode;
    @JsonProperty("fin_prdt_cd")
    private String financialProductCode;
    @JsonProperty("kor_co_nm")
    private String financialCompanyName;
    @JsonProperty("fin_prdt_nm")
    private String productName;
    @JsonProperty("max_limit")
    private Long maximumLimit;
}
