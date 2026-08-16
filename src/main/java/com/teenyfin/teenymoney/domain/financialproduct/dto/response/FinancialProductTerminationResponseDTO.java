package com.teenyfin.teenymoney.domain.financialproduct.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@ApiModel(description = "예·적금 중도해지 예상 또는 실행 결과")
public class FinancialProductTerminationResponseDTO {
    @ApiModelProperty(value = "가입 계약 아이디", example = "3")
    private final Long enrollmentId;
    @ApiModelProperty(value = "상품 유형", example = "SAVING")
    private final String productType;
    @ApiModelProperty(value = "가입기간 진행률(%)", example = "42")
    private final int progressPercent;
    @ApiModelProperty(value = "진행률 정책이 적용된 중도해지 연이율(%)", example = "0.30")
    private final BigDecimal appliedEarlyTerminationRate;
    @ApiModelProperty(value = "반환 원금", example = "100000")
    private final long principalAmount;
    @ApiModelProperty(value = "중도해지 이자", example = "120")
    private final long interestAmount;
    @ApiModelProperty(value = "최종 지급액", example = "100120")
    private final long finalAmount;
    @ApiModelProperty(value = "티니점수 변동량", example = "-4")
    private final int scoreChange;
    @ApiModelProperty(value = "실제 해지 실행 여부")
    private final boolean terminated;

    public FinancialProductTerminationResponseDTO(
            Long enrollmentId, String productType, int progressPercent,
            BigDecimal appliedEarlyTerminationRate, long principalAmount,
            long interestAmount, int scoreChange, boolean terminated) {
        this.enrollmentId = enrollmentId;
        this.productType = productType;
        this.progressPercent = progressPercent;
        this.appliedEarlyTerminationRate = appliedEarlyTerminationRate;
        this.principalAmount = principalAmount;
        this.interestAmount = interestAmount;
        this.finalAmount = Math.addExact(principalAmount, interestAmount);
        this.scoreChange = scoreChange;
        this.terminated = terminated;
    }
}
