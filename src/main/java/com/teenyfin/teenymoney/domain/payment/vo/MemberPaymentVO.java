package com.teenyfin.teenymoney.domain.payment.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class MemberPaymentVO {

    private String paymentPassword;
    private Integer paymentPasswordFailedCount;
    private LocalDateTime paymentLockedUntil;
}
