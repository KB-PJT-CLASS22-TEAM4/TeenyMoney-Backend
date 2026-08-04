package com.teenyfin.teenymoney.domain.wallet.service;

import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletDetailResponseDTO;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletTransactionVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

class WalletServiceTest {

    private WalletMapper walletMapper;
    private WalletService walletService;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        walletMapper = mock(WalletMapper.class);
        walletService = new WalletService(walletMapper, CLOCK);
    }

    @Test
    void getMyWalletDetailReturnsBalanceAndRecentTransactions() {
        // 지갑 조회 mock: memberId=17 -> 지갑(id=5, balance=150000)
        when(walletMapper.selectMemberWalletByMemberId(17L)).thenReturn(memberWallet());
        // 그 지갑(id=5)의 최근 거래내역 mock. limit=3은 WalletService 안의 RECENT_TRANSACTION_LIMIT과 맞아야 한다.
        when(walletMapper.selectRecentTransactions(5L, 3)).thenReturn(recentTransactions());

        WalletDetailResponseDTO response = walletService.getMyWalletDetail(17L);

        // 잔액 정보가 VO에서 DTO로 정확히 옮겨졌는지 확인
        assertEquals(5L, response.getWalletId());
        assertEquals(150000L, response.getBalance());
        assertEquals(LocalDateTime.of(2026, 7, 31, 16, 15, 43), response.getUpdatedAt());

        // 거래내역 리스트도 같이 담겨서 나오는지 확인
        assertEquals(1, response.getRecentTransactions().size());
        assertEquals("DEBIT", response.getRecentTransactions().get(0).getDirection());
        assertEquals(3500L, response.getRecentTransactions().get(0).getAmount());
        assertEquals(146500L, response.getRecentTransactions().get(0).getBalanceAfter());
        assertEquals("편의점 결제", response.getRecentTransactions().get(0).getDescription());
    }

    @Test
    void getMyWalletDetailWithMissingWalletThrowsWalletNotFound() {
        // 지갑이 아예 없는 상황을 가정: Mapper가 null을 반환하도록 설정
        when(walletMapper.selectMemberWalletByMemberId(17L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> walletService.getMyWalletDetail(17L));

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
    }

    private WalletVO memberWallet() {
        WalletVO wallet = new WalletVO();
        wallet.setId(5L);
        wallet.setMemberId(17L);
        wallet.setBalance(150000L);
        wallet.setType("MEMBER");
        wallet.setUpdatedAt(LocalDateTime.of(2026, 7, 31, 16, 15, 43));
        return wallet;
    }

    private List<WalletTransactionVO> recentTransactions() {
        WalletTransactionVO transaction = new WalletTransactionVO();
        transaction.setId(1L);
        transaction.setDirection("DEBIT");
        transaction.setAmount(3500L);
        transaction.setBalanceAfter(146500L);
        transaction.setDescription("편의점 결제");
        transaction.setCreatedAt(LocalDateTime.of(2026, 7, 31, 16, 15, 43));
        return List.of(transaction);
    }
}
