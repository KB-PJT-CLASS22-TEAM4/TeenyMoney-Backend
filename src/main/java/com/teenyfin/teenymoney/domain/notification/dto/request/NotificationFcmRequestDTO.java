package com.teenyfin.teenymoney.domain.notification.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationFcmRequestDTO {

    @NotBlank
    @Size(max = 255)
    private String fcmToken;
}
