package com.teenyfin.teenymoney.domain.report.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@ApiModel(description = "주차별 소비. 주차는 월요일에 시작해 일요일에 끝나고 그 달의 1일과 말일에서만 잘린다")
public class WeeklyTrendResponseDTO {

    @ApiModelProperty(value = "그 달의 몇 번째 주인지. 1부터 시작", example = "3")
    private final int weekNo;

    @ApiModelProperty(value = "주차 시작일", example = "2026-08-10")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate startDate;

    @ApiModelProperty(value = "주차 종료일", example = "2026-08-16")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private final LocalDate endDate;

    @ApiModelProperty(
            value = "그 주의 소비 금액. 아직 오지 않은 주차는 null이다. "
                    + "0원을 쓴 주와 아직 오지 않은 주를 구분하기 위해서다. 완료된 달에는 null이 없다",
            example = "12800")
    private final Long amount;

    @ApiModelProperty(value = "그 주의 결제 건수. 아직 오지 않은 주차는 null", example = "3")
    private final Integer paymentCount;
}
