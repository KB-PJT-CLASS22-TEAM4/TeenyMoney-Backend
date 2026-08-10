package com.teenyfin.teenymoney.domain.categoryPolicy.vo;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class CategoryPolicyVO {

    private Long id;
    private Long merchantCategoryId;
    private String merchantCategoryName;
    private String policy;
}
