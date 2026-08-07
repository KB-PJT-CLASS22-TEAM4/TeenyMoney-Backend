package com.teenyfin.teenymoney.domain.permission.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

@ApiModel(description = "오늘만 허용을 요청한 자녀 정보")
@Builder
@Getter
public class PermissionResponseChildDTO {

    @ApiModelProperty(value = "자녀 ID", example = "1")
    private Long id;

    @ApiModelProperty(value = "자녀 이름", example = "김첫째")
    private String name;

    @ApiModelProperty(value = "자녀 프로필 이미지 URL", example = "https://example.com/profile.png")
    private String profileImageUrl;
}
