package com.teenyfin.teenymoney.domain.permission.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class PermissionRequestDTO {

    private List<Long> categories;
    private String reason;
}
