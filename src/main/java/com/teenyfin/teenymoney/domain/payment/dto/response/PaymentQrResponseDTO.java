package com.teenyfin.teenymoney.domain.payment.dto.response;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

@ApiModel(description = "QR 코드 정보 및 카테고리 정책 관련 정보")
@Builder
@Getter
public class PaymentQrResponseDTO {

    @ApiModelProperty(value = "주문 정보 ID (UUID)", example = "e800ab29-9537-4fc3-9206-8075c402001b")
    private String orderId;

    @ApiModelProperty(value = "가맹점 이름", example = "스타 코인노래방")
    private String merchantName;

    @ApiModelProperty(value = "결제 금액", example = "2000")
    private Long amount;

    @ApiModelProperty(value = "현재 지갑 잔액", example = "15000")
    private Long balance;

    private CategoryPolicyResponseDTO categoryPolicy;

    @ApiModelProperty(value = "해당 카테고리에서 최근 30일간 결제한 횟수 (주의 단계인 경우에만 반환)", example = "3")
    private Integer totalCount;

    @ApiModelProperty(value = "해당 카테고리에서 최근 30일간 결제한 총 금액 (주의 단계인 경우에만 반환)", example = "10000")
    private Long totalAmount;
}
