package com.teenyfin.teenymoney.domain.permission.vo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
public class PermissionInsertVO {

    private Long id;
    private Long parentId;
    private Long childId;
    private String reason;
}
