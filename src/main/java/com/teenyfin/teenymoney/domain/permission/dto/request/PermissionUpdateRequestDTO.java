package com.teenyfin.teenymoney.domain.permission.dto.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Size;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PermissionUpdateRequestDTO {

    @ApiModelProperty(value = "오늘만 허용 요청 사유", example = "오늘이 친구 생일이라 다같이 PC방에서 놀기로 했어요")
    @Size(max = 255)
    private String reason;
}
