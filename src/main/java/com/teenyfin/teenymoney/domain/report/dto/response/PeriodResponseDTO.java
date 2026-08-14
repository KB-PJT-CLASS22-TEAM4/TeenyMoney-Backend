package com.teenyfin.teenymoney.domain.report.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@ApiModel(description = "조회 기간과 비교 기간")
public class PeriodResponseDTO {

    @ApiModelProperty(value = "조회한 월", example = "2026-08")
    private final String yearMonth;

    @ApiModelProperty(value = "조회 시작일. 항상 그 달 1일", example = "2026-08-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate startDate;

    @ApiModelProperty(
            value = "조회 종료일. 진행 중인 달은 오늘, 완료된 달은 말일",
            example = "2026-08-13")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate endDate;

    @ApiModelProperty(value = "IN_PROGRESS 또는 COMPLETED", example = "IN_PROGRESS")
    private final String status;

    @ApiModelProperty(
            value = "비교 기간 시작일. 진행 중인 달은 전월 1일, 완료된 달은 직전 달 1일",
            example = "2026-07-01")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate comparisonStartDate;

    @ApiModelProperty(
            value = "비교 기간 종료일. 전월에 대응 일자가 없으면 전월 말일로 자른다",
            example = "2026-07-13")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate comparisonEndDate;
}
