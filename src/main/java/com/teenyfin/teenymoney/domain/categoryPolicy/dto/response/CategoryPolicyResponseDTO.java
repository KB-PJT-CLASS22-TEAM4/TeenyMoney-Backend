package com.teenyfin.teenymoney.domain.categoryPolicy.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

@ApiModel(description = "카테고리 정책 DTO")
@Builder
@Getter
public class CategoryPolicyResponseDTO {

    @ApiModelProperty(value = "카테고리 정책 ID", example = "1")
    private Long id;

    @ApiModelProperty(value = "카테고리 이름", example = "편의점")
    private String merchantCategoryName;

    @ApiModelProperty(value = "정책 단계 (ALLOW/WATCH/BLOCK)", example = "ALLOW")
    private String policy;
}
