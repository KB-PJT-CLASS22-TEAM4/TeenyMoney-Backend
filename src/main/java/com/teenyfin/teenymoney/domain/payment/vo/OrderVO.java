package com.teenyfin.teenymoney.domain.payment.vo;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class OrderVO {

    private String merchantName;
    private Long categoryId;
    private Long amount;
}
