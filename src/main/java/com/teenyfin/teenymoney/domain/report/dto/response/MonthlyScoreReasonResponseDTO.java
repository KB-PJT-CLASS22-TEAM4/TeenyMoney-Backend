package com.teenyfin.teenymoney.domain.report.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@ApiModel(description = "티니점수 변화의 대표 원인")
public class MonthlyScoreReasonResponseDTO {

    @ApiModelProperty(value = "저장된 이벤트 코드", example = "LOAN_INSTALLMENT_OVERDUE")
    private final String eventCode;

    @ApiModelProperty(value = "저장된 설명. 서버가 새로 만들지 않는다", example = "대출 월별 상환 결과")
    private final String description;

    @ApiModelProperty(value = "변동 점수. 음수 가능", example = "-4")
    private final int amount;
}
