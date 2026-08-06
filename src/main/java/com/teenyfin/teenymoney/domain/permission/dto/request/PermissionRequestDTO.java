package com.teenyfin.teenymoney.domain.permission.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestDTO {

    private List<Long> categories;
    private String reason;
}
