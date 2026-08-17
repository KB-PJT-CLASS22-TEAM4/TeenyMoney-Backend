package com.teenyfin.teenymoney.domain.notification.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationVO {

    private Long id;
    private Long memberId;
    private String title;
    private String content;
    private NotificationReferenceType referenceType;
    private Long referenceId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
