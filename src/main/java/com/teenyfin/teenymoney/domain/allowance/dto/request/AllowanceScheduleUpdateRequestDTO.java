package com.teenyfin.teenymoney.domain.allowance.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;


@Getter
@Setter
@NoArgsConstructor
@ApiModel(description = "정기 용돈 스케줄 전체 수정 요청")
public class AllowanceScheduleUpdateRequestDTO {

    @ApiModelProperty(value = "용돈을 받을 자녀의 회원 아이디", required = true, example = "2")
    @NotNull(message = "childId는 필수입니다.")
    private Long childId;

    @ApiModelProperty(value = "용돈 지급 금액", required = true, example = "10000")
    @NotNull(message = "금액은 필수입니다.")
    @Positive(message = "금액은 0보다 커야 합니다.")
    private Long amount;

    @ApiModelProperty(value = "지급 주기: 주 단위 또는 월 단위", required = true, example = "WEEKLY or MONTHLY")
    @NotNull(message = "주 단위 혹은 월 단위 (cycleType)은 필수입니다.")
    @Pattern(regexp = "WEEKLY|MONTHLY", message = "cycleType은 WEEKLY 또는 MONTHLY만 가능합니다.")
    private String cycleType;

    @ApiModelProperty(value = "지급일: WEEKLY는 요일(1=월요일~7=일요일), MONTHLY는 일자(1~28)", required = true, example = "1")
    @NotNull(message = "paymentDay는 필수입니다.")
    private Integer paymentDay;
}
