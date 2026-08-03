package com.teenyfin.teenymoney.domain.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletTransactionVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

// "내 지갑 보기" 화면 하나에 필요한 걸 한 번에 담아 내려주는 응답 DTO.
// 잔액(WalletVO)과 거래내역(WalletTransactionVO 리스트)이 각각 다른 SQL로 조회되지만,
// 화면은 한 번에 같이 보여줘야 해서 API도 하나로 합쳤다.
@Getter
public class WalletDetailResponseDTO {

    private final Long walletId;
    private final Long balance;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime updatedAt;
    private final List<WalletTransactionResponseDTO> recentTransactions;

    private WalletDetailResponseDTO(WalletVO wallet, List<WalletTransactionVO> transactions) {
        this.walletId = wallet.getId();
        this.balance = wallet.getBalance();
        this.updatedAt = wallet.getUpdatedAt();
        // 거래내역 VO 리스트를 한 건씩 WalletTransactionResponseDTO로 변환해서 담는다
        this.recentTransactions = transactions.stream()
                .map(WalletTransactionResponseDTO::of)
                .toList();
    }

    public static WalletDetailResponseDTO of(WalletVO wallet, List<WalletTransactionVO> transactions) {
        return new WalletDetailResponseDTO(wallet, transactions);
    }
}
