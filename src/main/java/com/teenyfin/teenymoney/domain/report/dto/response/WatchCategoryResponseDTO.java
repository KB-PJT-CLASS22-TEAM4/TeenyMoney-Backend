package com.teenyfin.teenymoney.domain.report.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@ApiModel(description = "주의 업종별 결제")
public class WatchCategoryResponseDTO {

    @ApiModelProperty(value = "업종 카테고리 아이디", example = "9")
    private final Long categoryId;

    @ApiModelProperty(value = "업종 카테고리명", example = "게임")
    private final String categoryName;

    @ApiModelProperty(value = "결제 건수", example = "1")
    private final int paymentCount;

    @ApiModelProperty(value = "금액", example = "6500")
    private final long amount;
}
