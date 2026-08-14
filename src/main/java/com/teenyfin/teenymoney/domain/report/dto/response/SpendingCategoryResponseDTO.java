package com.teenyfin.teenymoney.domain.report.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@ApiModel(description = "카테고리별 소비")
public class SpendingCategoryResponseDTO {

    @ApiModelProperty(value = "업종 카테고리 아이디", example = "3")
    private final Long categoryId;

    @ApiModelProperty(value = "업종 카테고리명", example = "카페·디저트")
    private final String categoryName;

    @ApiModelProperty(value = "금액", example = "9500")
    private final long amount;

    @ApiModelProperty(value = "결제 건수", example = "3")
    private final int paymentCount;

    @ApiModelProperty(
            value = "전체 지출에서 차지하는 비중(정수 %). 반올림 때문에 합이 100이 아닐 수 있다",
            example = "25")
    private final int ratio;
}
