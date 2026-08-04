package com.teenyfin.teenymoney.domain.categoryPolicy.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@ApiModel(description = "카테고리 정책 리스트 DTO")
@Getter
public class CategoryPolicyUpdateRequestListDTO {

    @ApiModelProperty(value = "카테고리 정책 리스트")
    @NotEmpty
    @Valid
    private List<CategoryPolicyUpdateRequestDTO> categoryPolicyList;
}
