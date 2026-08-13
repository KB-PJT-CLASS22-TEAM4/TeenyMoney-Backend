package com.teenyfin.teenymoney.domain.notification.vo;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class MemberNotificationVO {

    private String fcmToken;
    private Boolean notiPayment;
    private Boolean notiQuest;
    private Boolean notiFinance;
    private Boolean notiAllowance;
}
