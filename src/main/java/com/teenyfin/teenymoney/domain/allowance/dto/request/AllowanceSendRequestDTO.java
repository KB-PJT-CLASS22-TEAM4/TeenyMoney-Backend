package com.teenyfin.teenymoney.domain.allowance.dto.request;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Getter
@Setter
@NoArgsConstructor
@ApiModel(description = "용돈 보내기 요청")
public class AllowanceSendRequestDTO {

    @ApiModelProperty(value = "보낼 금액(원)", required = true, example = "10000")
    @NotNull(message = "금액은 필수입니다.")
    @Positive(message = "금액은 0보다 커야 합니다.")
    private Long amount;
}
