package com.teenyfin.teenymoney.global.health.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HealthResponseDTO {

    private final String status;
    private final String time;
}