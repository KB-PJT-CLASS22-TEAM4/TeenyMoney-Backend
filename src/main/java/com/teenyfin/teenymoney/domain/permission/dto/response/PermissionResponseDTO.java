package com.teenyfin.teenymoney.domain.permission.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Builder
@Getter
public class PermissionResponseDTO {

    private Long id;
    private PermissionResponseChildDTO child;
    private List<String> categories;
    private String reason;
    private String status;
    private LocalDateTime createdAt;
}
