package com.teenyfin.teenymoney.domain.notification.dto.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingRequestDTO {

    @ApiModelProperty(value = "결제 관련 알림 수신 여부", example = "true")
    @NotNull
    private Boolean notificationPayment;

    @ApiModelProperty(value = "퀘스트 관련 알림 수신 여부", example = "true")
    @NotNull
    private Boolean notificationQuest;

    @ApiModelProperty(value = "금융 상품 가입 관련 알림 수신 여부", example = "true")
    @NotNull
    private Boolean notificationFinance;
}
