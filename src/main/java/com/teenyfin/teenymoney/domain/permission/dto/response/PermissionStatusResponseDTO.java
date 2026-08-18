package com.teenyfin.teenymoney.domain.permission.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@ApiModel(description = "이번 달 오늘만 허용 사용 현황 및 카테고리별 현재 상태")
@Builder
@Getter
public class PermissionStatusResponseDTO {

    @ApiModelProperty(value = "이번 달 오늘만 허용을 요청한 일수", example = "3")
    private int monthlyUsedCount;

    @ApiModelProperty(value = "이번 달 앞으로 요청 가능한 일수", example = "2")
    private int monthlyRemainingCount;

    @ApiModelProperty(value = "카테고리별 오늘 기준 현재 상태 목록")
    private List<PermissionCategoryStatusResponseDTO> categories;
}
