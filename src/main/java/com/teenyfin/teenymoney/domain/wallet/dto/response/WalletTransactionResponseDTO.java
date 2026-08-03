package com.teenyfin.teenymoney.domain.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletTransactionVO;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class WalletTransactionResponseDTO {

    private final Long id;
    private final String direction;
    private final Long amount;
    private final Long balanceAfter;
    private final String description;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime createdAt;

    private WalletTransactionResponseDTO(WalletTransactionVO transaction) {
        this.id = transaction.getId();
        this.direction = transaction.getDirection();
        this.amount = transaction.getAmount();
        this.balanceAfter = transaction.getBalanceAfter();
        this.description = transaction.getDescription();
        this.createdAt = transaction.getCreatedAt();
    }

    public static WalletTransactionResponseDTO of(WalletTransactionVO transaction) {
        return new WalletTransactionResponseDTO(transaction);
    }
}
