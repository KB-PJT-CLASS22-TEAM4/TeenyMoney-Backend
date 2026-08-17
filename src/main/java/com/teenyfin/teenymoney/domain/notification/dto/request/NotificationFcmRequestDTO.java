package com.teenyfin.teenymoney.domain.notification.dto.request;

import io.swagger.annotations.ApiModelProperty;
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

    @ApiModelProperty(value = "갱신할 FCM 토큰", example = "d1q2w3e4r5t6y7u8i9o0p")
    @NotBlank
    @Size(max = 255)
    private String fcmToken;
}
