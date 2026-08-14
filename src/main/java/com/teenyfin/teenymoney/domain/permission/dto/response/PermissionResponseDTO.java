package com.teenyfin.teenymoney.domain.permission.dto.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@ApiModel(description = "오늘만 허용 요청 정보")
@Builder
@Getter
public class PermissionResponseDTO {

    @ApiModelProperty(value = "오늘만 허용 요청 ID", example = "1")
    private Long id;

    @ApiModelProperty(value = "오늘만 허용을 요청한 카테고리 이름", example = "PC방·노래방")
    private String category;

    @ApiModelProperty(value = "오늘만 허용 요청 사유", example = "오늘이 친구 생일이라 다같이 PC방에서 놀기로 했어요")
    private String reason;

    @ApiModelProperty(value = "오늘만 허용 요청의 현재 상태, PENDING(대기)/APPROVED(승인)/REJECTED(거절)/EXPIRED(만료)", example = "PENDING")
    private String status;

    @ApiModelProperty(value = "오늘만 허용 요청 일시", example = "2026-08-07T02:10:29.109Z")
    private LocalDateTime createdAt;
}
