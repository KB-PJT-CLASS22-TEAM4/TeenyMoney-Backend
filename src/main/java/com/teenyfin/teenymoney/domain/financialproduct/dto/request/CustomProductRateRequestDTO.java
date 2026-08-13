package com.teenyfin.teenymoney.domain.financialproduct.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "부모 생성 예·적금의 기간별 기본금리")
public class CustomProductRateRequestDTO {
    @NotNull
    @ApiModelProperty(value = "가입기간(1, 3, 6, 12개월)", example = "12")
    private Integer termMonths;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @ApiModelProperty(value = "해당 기간의 기본금리(%)", example = "4.50")
    private BigDecimal interestRate;
}
