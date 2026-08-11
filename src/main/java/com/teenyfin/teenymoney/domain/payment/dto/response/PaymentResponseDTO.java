package com.teenyfin.teenymoney.domain.payment.dto.response;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@ApiModel(description = "결제 정보")
@Builder
@Getter
public class PaymentResponseDTO {

    @ApiModelProperty(value = "가맹점 이름", example = "스타 코인노래방")
    private String merchantName;

    @ApiModelProperty(value = "결제 금액", example = "2000")
    private Long amount;

    @ApiModelProperty(value = "결제 후 지갑 잔액", example = "13000")
    private Long balance;

    private CategoryPolicyResponseDTO categoryPolicy;

    @ApiModelProperty(value = "결제 일시", example = "2026-08-11T08:22:02.126Z")
    private LocalDateTime createdAt;
}
