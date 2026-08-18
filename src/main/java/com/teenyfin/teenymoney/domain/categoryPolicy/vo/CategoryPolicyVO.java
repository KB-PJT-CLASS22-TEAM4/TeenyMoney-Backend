package com.teenyfin.teenymoney.domain.categoryPolicy.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryPolicyVO {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String parentCategoryName;
    private CategoryPolicy policy;
}
