package com.teenyfin.teenymoney.domain.paymentPassword.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class PaymentPasswordVO {

    private String paymentPassword;
    private Integer paymentPasswordFailedCount;
    private LocalDateTime paymentLockedUntil;
}
