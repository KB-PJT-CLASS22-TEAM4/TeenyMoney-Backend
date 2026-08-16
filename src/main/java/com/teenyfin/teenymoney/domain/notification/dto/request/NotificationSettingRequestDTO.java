package com.teenyfin.teenymoney.domain.notification.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@ApiModel(description = "알림 수신 설정 DTO")
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingRequestDTO {

    @NotNull
    private Boolean notificationPayment;

    @NotNull
    private Boolean notificationQuest;

    @NotNull
    private Boolean notificationFinance;
}
