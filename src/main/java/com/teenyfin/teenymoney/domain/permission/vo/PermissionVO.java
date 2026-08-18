package com.teenyfin.teenymoney.domain.permission.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PermissionVO {

    private Long id;
    private Long parentId;
    private Long childId;
    private Long categoryId;
    private String category;
    private String reason;
    private PermissionStatus status;
    private LocalDateTime createdAt;
}
