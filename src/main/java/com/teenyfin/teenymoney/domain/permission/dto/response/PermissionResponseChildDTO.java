package com.teenyfin.teenymoney.domain.permission.dto.response;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class PermissionResponseChildDTO {

    private Long id;
    private String name;
    private String profileImageUrl;
}
