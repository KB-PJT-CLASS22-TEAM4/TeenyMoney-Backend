package com.teenyfin.teenymoney.domain.permission.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class PermissionVO {

    private Long id;
    private Long childId;
    private String categoryName;
    private String reason;
    private String status;
    private LocalDateTime createdAt;
}
