package com.teenyfin.teenymoney.domain.financialproduct.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@ApiModel(description = "완료 예금의 기간별 실제 누적 내역")
public class DepositCompletionPeriodResponseDTO {
    @ApiModelProperty("경과 개월")
    private int monthNo;
    @ApiModelProperty("기간 종료일")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate periodEndDate;
    @ApiModelProperty("원금")
    private long principalAmount;
    @ApiModelProperty("해당 시점까지 누적 이자")
    private long cumulativeInterestAmount;
    @ApiModelProperty("해당 시점의 원리금 합계")
    private long accumulatedAmount;
}
