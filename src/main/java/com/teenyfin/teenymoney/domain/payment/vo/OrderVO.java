package com.teenyfin.teenymoney.domain.payment.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO {

    private String merchantName;
    private Long categoryId;
    private Long amount;
}
