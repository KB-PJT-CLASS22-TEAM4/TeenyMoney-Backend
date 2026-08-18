package com.teenyfin.teenymoney.domain.permission.dto.response;

import com.teenyfin.teenymoney.domain.permission.vo.PermissionStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

@ApiModel(description = "카테고리별 오늘만 허용 현재 상태")
@Builder
@Getter
public class PermissionCategoryStatusResponseDTO {

    @ApiModelProperty(value = "카테고리 ID", example = "1")
    private Long categoryId;

    @ApiModelProperty(value = "카테고리 이름", example = "PC방·노래방")
    private String categoryName;

    @ApiModelProperty(value = "오늘 기준 상태, AVAILABLE(요청 가능)/PENDING(요청함, 대기중)/APPROVED(승인됨)/REJECTED(거절됨)/EXPIRED(만료됨)", example = "AVAILABLE")
    private PermissionStatus status;
}
