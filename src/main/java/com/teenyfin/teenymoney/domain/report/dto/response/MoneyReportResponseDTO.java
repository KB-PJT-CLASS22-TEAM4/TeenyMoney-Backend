package com.teenyfin.teenymoney.domain.report.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 머니 리포트 한 화면 분량.
 *
 * 다음 단계에서 summary(한눈에 보기 4종), insights(금융 습관), teenyScore(점수 변화)가
 * 추가된다. 지금은 필드 자체가 없다. 화면 입장에서 키가 없는 것과 null 인 것이 같으므로
 * 빈 DTO 를 미리 만들어 null 로 채우지 않는다.
 */
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

    @ApiModelProperty(value = "소비 상세")
    private final SpendingResponseDTO spending;

    @ApiModelProperty(value = "주의 업종 결제")
    private final WatchSpendingResponseDTO watchSpending;
}
