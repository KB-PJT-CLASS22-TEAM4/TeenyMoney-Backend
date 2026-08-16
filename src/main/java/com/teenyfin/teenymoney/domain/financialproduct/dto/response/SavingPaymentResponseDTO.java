package com.teenyfin.teenymoney.domain.financialproduct.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "자유적금 직접납입 결과")
public record SavingPaymentResponseDTO(
        @ApiModelProperty("송금 아이디") Long transferId,
        @ApiModelProperty("납입 금액") Long paidAmount,
        @ApiModelProperty("납입 후 누적 금액") Long accumulatedAmount) {
}
