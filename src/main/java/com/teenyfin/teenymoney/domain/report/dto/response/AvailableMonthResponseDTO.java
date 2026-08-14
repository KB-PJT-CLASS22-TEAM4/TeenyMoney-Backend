package com.teenyfin.teenymoney.domain.report.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@ApiModel(description = "월 선택 바텀시트에 노출할 월")
public class AvailableMonthResponseDTO {

    @ApiModelProperty(value = "월", example = "2026-07")
    private final String yearMonth;

    @ApiModelProperty(value = "IN_PROGRESS 또는 COMPLETED", example = "COMPLETED")
    private final String status;
}
