package com.teenyfin.teenymoney.domain.permission.dto.response;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class PermissionResponseWrapperDTO {

    private Boolean isExist;
    private PermissionResponseDTO permission;
}
