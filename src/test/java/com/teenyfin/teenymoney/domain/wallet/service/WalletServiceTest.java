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
import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WalletServiceTest {

    private WalletMapper walletMapper;
    private FamilyAccessService familyAccessService;
    private WalletService walletService;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final MemberPrincipal PARENT = new MemberPrincipal(1L, "PARENT");

    @BeforeEach
    void setUp() {
        walletMapper = mock(WalletMapper.class);
        familyAccessService = mock(FamilyAccessService.class);
        walletService = new WalletService(walletMapper, CLOCK, familyAccessService);
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


    @Test
    void getChildWalletDetailReturnsChildBalanceWhenAccessGranted() {
        // familyAccessService.requireChildAccess(...)는 stub을 따로 안 하면 mock의 기본
        // 동작(void 메서드는 그냥 아무것도 안 하고 리턴)을 쓴다 - 이게 "접근 허용됨" 상황을
        // 재현하는 가장 간단한 방법이다. 자녀(17L) 데이터는 기존 헬퍼를 그대로 재사용.
        when(walletMapper.selectMemberWalletByMemberId(17L)).thenReturn(memberWallet());
        when(walletMapper.selectRecentTransactions(5L, 3)).thenReturn(recentTransactions());

        WalletDetailResponseDTO response = walletService.getChildWalletDetail(PARENT, 17L);

        assertEquals(5L, response.getWalletId());
        assertEquals(150000L, response.getBalance());
        assertEquals(1, response.getRecentTransactions().size());

        // 권한 검증 메서드가 정확히 (PARENT, 17L)로 호출됐는지까지 확인한다.
        // 이게 없으면 "우연히 통과"와 "검증을 거쳐서 통과"를 구분할 수 없다.
        verify(familyAccessService).requireChildAccess(PARENT, 17L);
    }

    @Test
    void getChildWalletDetailWithForbiddenAccessThrowsAuthForbiddenAndSkipsWalletLookup() {
        // doThrow(...).when(mock).메서드(...) 는 "이 void 메서드가 불리면 예외를 던져라"는 뜻.
        // requireChildAccess()는 리턴값이 없는 void라서 when(...).thenThrow(...) 문법을
        // 못 쓰고 반드시 이 형태를 써야 한다 (Mockito 규칙).
        doThrow(new BusinessException(CommonErrorCode.AUTH_FORBIDDEN))
                .when(familyAccessService).requireChildAccess(any(), anyLong());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> walletService.getChildWalletDetail(PARENT, 17L));

        assertEquals(CommonErrorCode.AUTH_FORBIDDEN, exception.getErrorCode());

        // 권한 검증에서 이미 막혔으니, 그 뒤의 지갑 조회 쿼리는 아예 실행되면 안 된다.
        // never()로 "이 메서드가 한 번도 안 불렸다"를 확인 — "검증이 먼저"라는 순서를 못박는다.
        verify(walletMapper, never()).selectMemberWalletByMemberId(anyLong());
    }

    @Test
    void getChildWalletDetailWithMissingChildWalletThrowsWalletNotFound() {
        // 권한은 통과(mock 기본값)했지만, 그 자녀가 아직 지갑이 없는 상황(null 리턴)을 재현.
        when(walletMapper.selectMemberWalletByMemberId(17L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> walletService.getChildWalletDetail(PARENT, 17L));

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getChildTransactionsPassesChildIdAndFiltersToMapperWhenAccessGranted() {
        when(walletMapper.selectMemberWalletByMemberId(17L)).thenReturn(memberWallet());
        when(walletMapper.selectTransactions(5L, "CREDIT", LocalDate.of(2026, 7, 4), "ASC"))
                .thenReturn(recentTransactions());

        List<WalletTransactionResponseDTO> response = walletService.getChildTransactions(
                PARENT, 17L, TransactionTypeFilter.CREDIT, TransactionPeriod.MONTH, TransactionSortOrder.ASC);

        assertEquals(1, response.size());
        verify(familyAccessService).requireChildAccess(PARENT, 17L);
        verify(walletMapper).selectTransactions(5L, "CREDIT", LocalDate.of(2026, 7, 4), "ASC");
    }

    @Test
    void getChildTransactionsWithForbiddenAccessThrowsAuthForbidden() {
        doThrow(new BusinessException(CommonErrorCode.AUTH_FORBIDDEN))
                .when(familyAccessService).requireChildAccess(any(), anyLong());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> walletService.getChildTransactions(PARENT, 17L,
                        TransactionTypeFilter.ALL, TransactionPeriod.MONTH, TransactionSortOrder.DESC));

        assertEquals(CommonErrorCode.AUTH_FORBIDDEN, exception.getErrorCode());
        verify(walletMapper, never()).selectMemberWalletByMemberId(anyLong());
    }

    @Test
    void getChildTransactionsWithMissingChildWalletThrowsWalletNotFound() {
        when(walletMapper.selectMemberWalletByMemberId(17L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> walletService.getChildTransactions(PARENT, 17L,
                        TransactionTypeFilter.ALL, TransactionPeriod.WEEK, TransactionSortOrder.DESC));

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
