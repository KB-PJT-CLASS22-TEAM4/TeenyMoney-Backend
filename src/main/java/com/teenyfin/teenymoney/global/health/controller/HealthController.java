package com.teenyfin.teenymoney.global.health.controller;


import com.teenyfin.teenymoney.global.health.dto.response.DatabaseHealthResponseDTO;
import com.teenyfin.teenymoney.global.health.dto.response.HealthResponseDTO;
import com.teenyfin.teenymoney.global.health.service.HealthService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private static final ZoneId KOREA_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HealthService healthService;

    @GetMapping
    public ApiResponse<HealthResponseDTO> health() {

        String koreaTime = ZonedDateTime.now(KOREA_ZONE_ID)
                .format(FORMATTER);

        HealthResponseDTO response =
                HealthResponseDTO.builder()
                        .status("UP")
                        .time(koreaTime)
                        .build();

        return ApiResponse.ok(response);
    }

    @GetMapping("/db")
    public ResponseEntity<ApiResponse<DatabaseHealthResponseDTO>> databaseHealth() {

        DatabaseHealthResponseDTO response = healthService.checkDatabaseHealth();

        return ResponseEntity.ok(
                ApiResponse.ok(response)
        );
    }
}