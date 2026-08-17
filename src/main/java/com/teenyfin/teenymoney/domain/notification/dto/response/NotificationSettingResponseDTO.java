package com.teenyfin.teenymoney.domain.notification.dto.response;

import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class NotificationSettingResponseDTO {

    @ApiModelProperty(value = "결제 관련 알림 수신 여부", example = "true")
    private Boolean notificationPayment;

    @ApiModelProperty(value = "퀘스트 관련 알림 수신 여부", example = "true")
    private Boolean notificationQuest;

    @ApiModelProperty(value = "금융 상품 가입 관련 알림 수신 여부", example = "true")
    private Boolean notificationFinance;
}
