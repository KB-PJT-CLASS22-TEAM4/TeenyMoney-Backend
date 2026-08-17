package com.teenyfin.teenymoney.domain.notification.dto.response;

import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class NotificationResponseDTO {

    @ApiModelProperty(value = "알림 ID", example = "1")
    private Long id;

    @ApiModelProperty(value = "알림을 수신한 회원 ID", example = "1")
    private Long memberId;

    @ApiModelProperty(value = "알림 제목", example = "결제가 완료됐어요")
    private String title;

    @ApiModelProperty(value = "알림 내용", example = "GS25 강남점 · 3,200원")
    private String content;

    @ApiModelProperty(value = "알림이 가리키는 대상의 유형", example = "PAYMENT")
    private NotificationReferenceType referenceType;

    @ApiModelProperty(value = "알림이 가리키는 대상의 ID", example = "1")
    private Long referenceId;

    @ApiModelProperty(value = "읽음 여부", example = "false")
    private Boolean isRead;

    @ApiModelProperty(value = "알림 생성 일시", example = "2026-08-07T02:10:29.109Z")
    private LocalDateTime createdAt;
}
