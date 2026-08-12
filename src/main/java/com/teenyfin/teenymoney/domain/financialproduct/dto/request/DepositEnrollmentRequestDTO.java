package com.teenyfin.teenymoney.domain.financialproduct.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "예금 가입 요청")
public class DepositEnrollmentRequestDTO {
    @NotNull
    @Positive
    private Long productId;
    @NotNull
    @Positive
    private Long amount;
    @NotNull
    @ApiModelProperty(example = "12")
    private Integer termMonths;
}
