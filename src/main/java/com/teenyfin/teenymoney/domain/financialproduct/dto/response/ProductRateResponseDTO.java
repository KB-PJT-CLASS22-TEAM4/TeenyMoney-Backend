package com.teenyfin.teenymoney.domain.financialproduct.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@ApiModel(description = "가입 기간별 예·적금 예상 금리")
public class ProductRateResponseDTO {
    @ApiModelProperty(value = "가입 기간(개월)", example = "12")
    private final int termMonths;
    @ApiModelProperty(value = "금감원 공시 기본금리(%)", example = "3.50")
    private final BigDecimal baseRate;
    @ApiModelProperty(value = "티니점수 등급 우대금리(%p)", example = "2.00")
    private final BigDecimal teenyBonusRate;
    @ApiModelProperty(value = "예상 적용금리(%)", example = "5.50")
    private final BigDecimal expectedAppliedRate;

    public ProductRateResponseDTO(
            int termMonths,
            BigDecimal baseRate,
            BigDecimal teenyBonusRate) {
        this.termMonths = termMonths;
        this.baseRate = baseRate;
        this.teenyBonusRate = teenyBonusRate;
        this.expectedAppliedRate = baseRate.add(teenyBonusRate);
    }
}
