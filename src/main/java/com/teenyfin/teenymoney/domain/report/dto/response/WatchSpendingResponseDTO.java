package com.teenyfin.teenymoney.domain.report.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@ApiModel(description = "주의 업종 결제. 결제 시점에 WATCH 정책이 적용된 성공 결제만 센다")
public class WatchSpendingResponseDTO {

    @ApiModelProperty(value = "주의 업종 결제 건수", example = "1")
    private final int paymentCount;

    @ApiModelProperty(value = "주의 업종 결제 금액", example = "6500")
    private final long amount;

    @ApiModelProperty(value = "같은 기간 전체 결제 건수. '7회 중 1회' 표현에 쓴다", example = "7")
    private final int totalPaymentCount;

    @ApiModelProperty(value = "비교 기간의 주의 업종 결제 건수", example = "0")
    private final int comparisonCount;

    @ApiModelProperty(value = "주의 업종별 내역. 금액 내림차순")
    private final List<WatchCategoryResponseDTO> categories;
}
