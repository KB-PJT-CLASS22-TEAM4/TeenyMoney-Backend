package com.teenyfin.teenymoney.domain.permission.dto.response;

import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class PermissionResponseWrapperDTO {

    @ApiModelProperty(value = "오늘 날짜에 생성된 오늘만 허용 요청의 존재 여부", example = "true")
    private Boolean isExist;

    private PermissionResponseDTO permission;
}
