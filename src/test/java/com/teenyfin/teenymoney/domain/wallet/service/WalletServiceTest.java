package com.teenyfin.teenymoney.domain.wallet.service;

import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionPeriod;
import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionSortOrder;
import com.teenyfin.teenymoney.domain.wallet.dto.request.TransactionTypeFilter;
import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletDetailResponseDTO;
import com.teenyfin.teenymoney.domain.wallet.dto.response.WalletTransactionResponseDTO;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletTransactionVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

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


    // Type = CREDIT, period = MONTH 를 넘기면 Mapper가 diretion="CREDIT", startDate=1개월 전 날짜로 호출되는지
    @Test
    void getMyTransactionsWithCreditFilterPassesDirectionAndComputedStartDate() {
        // memberId=17 -> 지갑(id=5)
        when(walletMapper.selectMemberWalletByMemberId(17L)).thenReturn(memberWallet());
        //CLOCK이 2026-08-04로 고정되어 있어 1개월 전 2026-07-04가 나와야 함.
        // type == CREDIT이면 direction 파라미터가 "CREDIT" 문자열로 넘어가는지도 확인 해야함
        when(walletMapper.selectTransactions(5L, "CREDIT", LocalDate.of(2026, 7, 4), "ASC"))
                .thenReturn(recentTransactions());

        List<WalletTransactionResponseDTO> response = walletService.getMyTransactions(17L, TransactionTypeFilter.CREDIT
        , TransactionPeriod.MONTH, TransactionSortOrder.ASC);

        assertEquals(1, response.size());
        assertEquals("DEBIT", response.get(0).getDirection());
        //Mapper가 정확히 이 4개 값으로 호출됐는지 확인
        verify(walletMapper).selectTransactions(5L, "CREDIT", LocalDate.of(2026, 7, 4), "ASC");
    }


    // type=ALL 을 넘기면 Mapper에는 direction 으로 null이 넘어가는가 테스트
    @Test
    void getMyTransactionsWithAllTypePassesNullDirectionToMapper() {
        when(walletMapper.selectMemberWalletByMemberId(17L)).thenReturn(memberWallet());
        // type=ALL이면 Service가 Mapper에 direction으로 null을 넘겨야 한다
        // (그래야 XML의 <if>가 조건을 빼고 "전체 조회"가 됨)
        when(walletMapper.selectTransactions(5L, null, LocalDate.of(2026, 5,4 ), "DESC"))
                .thenReturn(recentTransactions());

        List<WalletTransactionResponseDTO> response = walletService.getMyTransactions(17L, TransactionTypeFilter.ALL, TransactionPeriod.THREE_MONTHS
        , TransactionSortOrder.DESC);

        assertEquals(1, response.size());
        verify(walletMapper).selectTransactions(5L, null, LocalDate.of(2026, 5, 4), "DESC");
    }


    //지갑이 없는 회원이 거래내역 조회하면 WALLET_NOT_FOUND가 던져지는가?
    @Test
    void getMyTransactionsWithMissingWalletThrowsWalletNotFound() {
        when(walletMapper.selectMemberWalletByMemberId(17L)).thenReturn(null);

        // assertThrows: "이 코드를 실행하면 반드시 이 예외 타입이 던져져야 한다"를 검증하는 JUnit 기능.
        // 람다(() -> ...) 안의 코드를 실행해보고, 예외가 안 던져지면 이 assertThrows 자체가 테스트 실패로 처리된다.
        BusinessException exception = assertThrows(BusinessException.class, () -> walletService.getMyTransactions(17L,
                TransactionTypeFilter.ALL, TransactionPeriod.WEEK, TransactionSortOrder.DESC));

        // 던져진 예외 "안"에 정확히 WALLET_NOT_FOUND가 들어있는지까지 확인
        // (그냥 BusinessException이 아무거나 던져진 걸로는 부족하고, 코드가 정확해야 함)
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
