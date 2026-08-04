package com.teenyfin.teenymoney.domain.categoryPolicy.dto.response;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class CategoryPolicyResponseDTO {

    Long id;
    String merchantCategoryName;
    String policy;
}
