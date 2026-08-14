package com.teenyfin.teenymoney.domain.report.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@ApiModel(description = "소비 상세. 자녀 MEMBER 지갑의 성공 결제만 집계한다")
public class SpendingResponseDTO {

    @ApiModelProperty(value = "조회 기간 총 지출", example = "38200")
    private final long totalAmount;

    @ApiModelProperty(value = "조회 기간 결제 건수", example = "7")
    private final int paymentCount;

    @ApiModelProperty(value = "비교 기간 총 지출", example = "32100")
    private final long comparisonAmount;

    @ApiModelProperty(value = "비교 기간 결제 건수", example = "6")
    private final int comparisonCount;

    @ApiModelProperty(value = "금액 증감. 양수면 늘어난 것", example = "6100")
    private final long comparisonAmountDelta;

    @ApiModelProperty(value = "횟수 증감. 양수면 늘어난 것", example = "1")
    private final int comparisonCountDelta;

    @ApiModelProperty(value = "주차별 소비. 그 달의 모든 주차가 들어간다")
    private final List<WeeklyTrendResponseDTO> weeklyTrend;

    @ApiModelProperty(
            value = "카테고리별 소비. 금액 내림차순 → 횟수 내림차순 → 이름 오름차순으로 "
                    + "정렬되어 있다. 상위 몇 개만 노출할지는 화면이 정한다")
    private final List<SpendingCategoryResponseDTO> categories;
}
