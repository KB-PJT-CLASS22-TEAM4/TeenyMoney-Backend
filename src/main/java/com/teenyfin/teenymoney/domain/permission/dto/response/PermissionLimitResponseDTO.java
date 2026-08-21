package com.teenyfin.teenymoney.domain.permission.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@ApiModel(description = "자녀의 월간 오늘만 허용 한도 현황")
public class PermissionLimitResponseDTO {
    private Long childId;
    @ApiModelProperty("티니등급에 설정된 기본 사용 가능 일수")
    private int gradeDefaultLimit;
    @ApiModelProperty("부모 설정값, 아직 설정하지 않았으면 null")
    private Integer parentConfiguredLimit;
    @ApiModelProperty("현재 실제 적용되는 사용 가능 일수")
    private int effectiveLimit;
    @ApiModelProperty("이번 달 요청한 서로 다른 날짜 수")
    private int usedDays;
    @ApiModelProperty("이번 달 남은 요청 가능 일수")
    private int remainingDays;
    @ApiModelProperty("부모 설정값 적용 여부")
    private boolean customizedByParent;
}
