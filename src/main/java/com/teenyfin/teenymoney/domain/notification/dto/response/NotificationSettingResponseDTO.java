package com.teenyfin.teenymoney.domain.notification.dto.response;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class NotificationSettingResponseDTO {

    private Boolean notificationPayment;
    private Boolean notificationQuest;
    private Boolean notificationFinance;
}
