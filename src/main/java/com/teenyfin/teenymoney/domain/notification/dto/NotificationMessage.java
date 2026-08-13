package com.teenyfin.teenymoney.domain.notification.dto;

import com.google.firebase.messaging.Notification;
import lombok.Builder;

@Builder
public record NotificationMessage(
        String title,
        String body
) {

    public static NotificationMessage of(String title, String body) {
        return NotificationMessage.builder()
                .title(title)
                .body(body)
                .build();
    }

    public Notification toNotification() {
        return Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();
    }
}
