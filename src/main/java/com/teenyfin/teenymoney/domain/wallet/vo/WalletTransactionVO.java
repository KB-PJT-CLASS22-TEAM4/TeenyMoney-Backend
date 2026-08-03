package com.teenyfin.teenymoney.domain.wallet.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class WalletTransactionVO {

    private Long id;
    private String direction;
    private Long amount;
    private Long balanceAfter;
    private String description;
    private LocalDateTime createdAt;
}
