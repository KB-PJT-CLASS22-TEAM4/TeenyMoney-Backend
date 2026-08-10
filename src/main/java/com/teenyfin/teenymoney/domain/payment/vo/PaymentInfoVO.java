package com.teenyfin.teenymoney.domain.payment.vo;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class PaymentInfoVO {

    private Long walletId;
    private String merchantName;
    private Long categoryPolicyId;
    private Long amount;
}
