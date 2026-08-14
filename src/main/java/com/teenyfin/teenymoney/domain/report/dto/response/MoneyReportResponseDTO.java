package com.teenyfin.teenymoney.domain.report.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 머니 리포트 한 화면 분량. */
@Getter
@AllArgsConstructor
@ApiModel(description = "월간 머니 리포트")
public class MoneyReportResponseDTO {

    @ApiModelProperty(value = "조회 기간과 비교 기간")
    private final PeriodResponseDTO period;

    @ApiModelProperty(value = "연령 모드")
    private final AudienceResponseDTO audience;

    @ApiModelProperty(value = "월 선택에 노출할 월 목록. 가입 월부터 현재 월까지, 최신 월이 먼저")
    private final List<AvailableMonthResponseDTO> availableMonths;

    @ApiModelProperty(value = "이번 달 한눈에 보기. 네 가지 돈의 흐름")
    private final SummaryResponseDTO summary;

    @ApiModelProperty(value = "금융 습관 인사이트. 보여줄 사실이 없으면 빈 배열")
    private final List<InsightResponseDTO> insights;

    @ApiModelProperty(value = "소비 상세")
    private final SpendingResponseDTO spending;

    @ApiModelProperty(value = "주의 업종 결제")
    private final WatchSpendingResponseDTO watchSpending;

    @ApiModelProperty(value = "이달의 티니점수 변화. 기간 내 이력이 없으면 null")
    private final MonthlyScoreResponseDTO teenyScore;
}
