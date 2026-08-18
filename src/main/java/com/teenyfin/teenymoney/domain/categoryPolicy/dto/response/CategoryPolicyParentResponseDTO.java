package com.teenyfin.teenymoney.domain.categoryPolicy.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@ApiModel(description = "상위 카테고리 그룹 DTO")
@Builder
@Getter
public class CategoryPolicyParentResponseDTO {

    @ApiModelProperty(value = "상위 카테고리 이름", example = "음식")
    private String name;

    @ApiModelProperty(value = "카테고리 정책 리스트")
    private List<CategoryPolicyResponseDTO> categoryPolicyList;
}
