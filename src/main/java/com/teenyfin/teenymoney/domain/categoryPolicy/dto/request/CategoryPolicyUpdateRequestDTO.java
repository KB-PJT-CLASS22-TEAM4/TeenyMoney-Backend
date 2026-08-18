package com.teenyfin.teenymoney.domain.categoryPolicy.dto.request;

import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicy;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

import javax.validation.constraints.NotNull;

@ApiModel(description = "카테고리 정책 DTO")
@Getter
public class CategoryPolicyUpdateRequestDTO {

    @ApiModelProperty(value = "카테고리 정책 ID", example = "1")
    @NotNull
    private Long id;

    @ApiModelProperty(value = "정책 단계 (ALLOW/WATCH/BLOCK)", example = "ALLOW")
    @NotNull
    private CategoryPolicy policy;
}
