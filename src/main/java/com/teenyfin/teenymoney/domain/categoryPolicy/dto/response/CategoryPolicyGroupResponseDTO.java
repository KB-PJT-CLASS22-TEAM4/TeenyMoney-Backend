package com.teenyfin.teenymoney.domain.categoryPolicy.dto.response;

import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicy;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@ApiModel(description = "정책 그룹 DTO")
@Builder
@Getter
public class CategoryPolicyGroupResponseDTO {

    @ApiModelProperty(value = "정책 단계 (ALLOW/WATCH/BLOCK)", example = "ALLOW")
    private CategoryPolicy policy;

    @ApiModelProperty(value = "카테고리 정책 리스트")
    private List<CategoryPolicyResponseDTO> categoryPolicyList;
}
