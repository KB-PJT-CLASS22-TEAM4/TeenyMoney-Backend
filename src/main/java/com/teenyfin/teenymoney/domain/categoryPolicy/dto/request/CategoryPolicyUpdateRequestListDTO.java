package com.teenyfin.teenymoney.domain.categoryPolicy.dto.request;

import lombok.Getter;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Getter
public class CategoryPolicyUpdateRequestListDTO {

    @NotEmpty
    @Valid
    private List<CategoryPolicyUpdateRequestDTO> categoryPolicyList;
}
