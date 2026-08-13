package com.teenyfin.teenymoney.domain.notification.dto.response;

import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class NotificationResponseDTO {

    private Long id;
    private Long memberId;
    private String title;
    private String content;
    private NotificationReferenceType referenceType;
    private Long referenceId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
