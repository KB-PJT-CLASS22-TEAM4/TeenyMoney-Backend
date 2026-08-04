package com.teenyfin.teenymoney.domain.categoryPolicy.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class CategoryPolicyGroupResponseDTO {

    String policy;
    List<CategoryPolicyResponseDTO> categoryPolicyList;
}
