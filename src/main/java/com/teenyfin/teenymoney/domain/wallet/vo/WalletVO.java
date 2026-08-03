package com.teenyfin.teenymoney.domain.wallet.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class WalletVO {

    private Long id;
    private Long memberId;
    private Long balance;
    private String type;
    private LocalDateTime updatedAt;
}
