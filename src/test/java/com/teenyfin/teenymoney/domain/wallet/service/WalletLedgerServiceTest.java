package com.teenyfin.teenymoney.domain.wallet.service;

import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.ReferenceType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletLedgerServiceTest {


    // 가짜(mock)를 넣어서, "credit/debit이 매퍼를 정확한 값으로 호출하는지"만 검증한다.
    private WalletMapper walletMapper;
    private WalletLedgerService walletLedgerService;


    @BeforeEach
    void setUp() {
        walletMapper = mock(WalletMapper.class);
        // 테스트 대상(SUT: System Under Test)을 생성자에 가짜 매퍼를 직접 꽂아서 만든다.
        // Spring 컨테이너 없이 그냥 new로 만드는 거라 빠르고 단순하다.
        walletLedgerService = new WalletLedgerService(walletMapper);
    }
    @Test
    void creditIncreasesBalanceAndInsertsCreditHistory() {

        //given: 지갑(id=5)의 현재 잔액이 10000원이라 가정
        // selectWalletForUpdate(5L)를 호출하면 이 지갑을 돌려주도록 미리 정함.
        WalletVO wallet = new WalletVO();
        wallet.setId(5L);
        wallet.setBalance(10000L);
        when(walletMapper.selectWalletForUpdate(5L)).thenReturn(wallet);
        System.out.println("[BEFORE] 지갑 잔액 = " + wallet.getBalance());

        // when: 3000원을 CHARGE(충전) 참조로 입금(credit) 실행.
        // refId=99L은 "이 3000원이 어느 charge 시도에서 온 건지"를 가리키는 T_WLT_CHARGE_L.id라고 가정.
        walletLedgerService.credit(5L, 3000L, ReferenceType.CHARGE, 99L, "테스트");

        // then: 10000 + 3000 = 13000으로 잔액이 갱신 요청됐는지 확인.
        // verify()는 "이 메서드가 정확히 이 인자로 호출됐는지"를 사후에 검사하는 Mockito 문법.
        verify(walletMapper).updateBalance(5L, 13000L);
        System.out.println("[AFTER] updateBalance가 호출된 새 잔액 = 13000 (기대값과 일치해야 통과)");

        // then: 원장에도 CREDIT 방향으로, 갱신된 잔액(13000)과 함께,
        // CHARGE/99L이라는 참조 정보로 정확히 한 줄 기입 요청이 갔는지 확인.

        verify(walletMapper).insertWalletHistory(
                5L, "CREDIT", 3000L, 13000L, "CHARGE", 99L, "테스트");
        System.out.println("[LEDGER] direction=CREDIT, amount=3000, balanceAfter=13000, refType=CHARGE, refId=99");
    }

    @Test
    void debitDecreasesBalanceAndInsertsDebitHistory() {

        // given: 지갑(id=5)의 현재 잔액이 10000원이라 가정
        WalletVO wallet = new WalletVO();
        wallet.setId(5L);
        wallet.setBalance(10000L);
        when(walletMapper.selectWalletForUpdate(5L)).thenReturn(wallet);
        System.out.println("[BEFORE] 지갑 잔액 = " + wallet.getBalance());

        // when: 4000원을 TRANSFER(송금) 참조로 출금(debit) 실행.
        // refId=77L은 "이 4000원이 어느 송금 시도에서 나간 건지"를 가리키는 T_WLT_TRF_L.id라고 가정.
        walletLedgerService.debit(5L, 4000L, ReferenceType.TRANSFER, 77L, "테스트");

        // then: 10000 - 4000 = 6000으로 잔액이 갱신 요청됐는지 확인.
        verify(walletMapper).updateBalance(5L, 6000L);
        System.out.println("[AFTER] updateBalance가 호출된 새 잔액 = 6000 (기대값과 일치해야 통과)");

        // then: 원장에도 DEBIT 방향으로, 갱신된 잔액(6000)과 함께,
        // TRANSFER/77L이라는 참조 정보로 정확히 한 줄 기입 요청이 갔는지 확인.
        verify(walletMapper).insertWalletHistory(
                5L, "DEBIT", 4000L, 6000L, "TRANSFER", 77L, "테스트");
        System.out.println("[LEDGER] direction=DEBIT, amount=4000, balanceAfter=6000, refType=TRANSFER, refId=77");

    }

    @Test
    void debitWithInsufficientBalanceThrowsInsufficientBalanceException() {
        // given: 지갑(id=5)의 현재 잔액이 3000원 - 출금하려는 금액(5000원) 인 상태

        WalletVO wallet = new WalletVO();
        wallet.setBalance(3000L);
        when(walletMapper.selectWalletForUpdate(5L)).thenReturn(wallet);
        System.out.println("[BEFORE] 지갑 잔액 = " + wallet.getBalance());

        // when & then: 5000원을 출금하려 하면 잔액 부족 예외가 터져야 한다.
        // assertThrows: 람다(() -> ...) 안의 코드를 실행해보고, 지정한 예외 타입이 안 던져지면
        // assertThrows 자체가 테스트 실패로 처리된다. 반환값은 실제로 던져진 예외 객체.
        BusinessException exception = assertThrows(BusinessException.class, () -> walletLedgerService.debit(5L, 5000L, ReferenceType.TRANSFER, 77L, "테스트"));

        // 그냥 아무 BusinessException이나 던져진 걸로는 부족하고, 정확히 INSUFFICIENT_BALANCE 코드인지까지 확인.
        assertEquals(WalletErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());
        System.out.println("[EXCEPTION] " + exception.getErrorCode() + " - " + exception.getMessage());

        // then: 예외가 잔액 검증 단계(3단계)에서 바로 터졌으니, 그 뒤에 있는 4~6단계
        // (잔액 갱신, 원장 기입)는 절대 호출되면 안 된다 — "반쪽 상태 없음"을 확인하는 부분.
        // verify(mock, never())는 "이 메서드가 한 번도 호출 안 됐는지"를 검증하는 Mockito 문법.
        verify(walletMapper, never()).updateBalance(anyLong(), anyLong());
        verify(walletMapper, never()).insertWalletHistory(
                anyLong(), anyString(), anyLong(), anyLong(), anyString(), anyLong(), any());
        System.out.println("[VERIFY] updateBalance/insertWalletHistory 모두 호출되지 않음 (잔액/원장 변경 없음)");

    }

    @Test
    void creditWithNonPositiveAmountThrowsInvalidTransferAmountException() {
        // given: 이번엔 지갑을 mock으로 준비할 필요조차 없다.
        // amount 검증이 지갑 조회(1단계 validateAmount → 2단계 findWalletForUpdateOrThrow)보다
        // 먼저 일어나니까, 지갑 정보는 아예 쓰이지도 않는다.

        // when & then: 0원을 입금하려 하면 잘못된 금액 예외가 터져야 한다.
        BusinessException exception = assertThrows(BusinessException.class, () -> walletLedgerService.credit(5L, 0L, ReferenceType.CHARGE, 99L, "테스트"));

        assertEquals(WalletErrorCode.INVALID_TRANSFER_AMOUNT, exception.getErrorCode());
        System.out.println("[EXCEPTION] " + exception.getErrorCode() + " - " + exception.getMessage());

        // then: 금액 검증(1단계)에서 바로 막혔으니, 그 뒤 단계인 지갑 조회조차 시도하면 안 된다.
        // 이걸 확인하면 "검증 순서가 진짜로 amount 먼저, 지갑 조회는 나중"이라는 게 증명된다.
        verify(walletMapper, never()).selectWalletForUpdate(anyLong());
        verify(walletMapper, never()).updateBalance(anyLong(), anyLong());
        verify(walletMapper, never()).insertWalletHistory(
                anyLong(), anyString(), anyLong(), anyLong(), anyString(), anyLong(), any());
        System.out.println("[VERIFY] selectWalletForUpdate/updateBalance/insertWalletHistory 모두 호출되지 않음");
    }

    @Test
    void debitWithMissingWalletThrowsWalletNotFoundException() {

        // given: selectWalletForUpdate(5L)를 호출하면 지갑이 없다는 뜻으로 null을 돌려주도록 세팅.
        // (실제 DB에서 존재하지 않는 walletId로 FOR UPDATE 조회하면 결과가 0행 -> MyBatis는 null을 반환)
        when(walletMapper.selectWalletForUpdate(5L)).thenReturn(null);
        System.out.println("[BEFORE] selectWalletForUpdate(5L) -> null (지갑 없음)");

        // when & then: 존재하지 않는 지갑으로 출금을 시도하면 WALLET_NOT_FOUND 예외가 터져야 한다.
        BusinessException exception = assertThrows(BusinessException.class,
                () -> walletLedgerService.debit(5L, 1000L, ReferenceType.TRANSFER, 77L, "테스트"));

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND, exception.getErrorCode());
        System.out.println("[EXCEPTION] " + exception.getErrorCode() + " - " + exception.getMessage());

        // then: 지갑 자체가 없으니 잔액 검증도, 갱신도, 원장 기입도 전부 일어나면 안 된다.
        verify(walletMapper, never()).updateBalance(anyLong(), anyLong());
        verify(walletMapper, never()).insertWalletHistory(
                anyLong(), anyString(), anyLong(), anyLong(), anyString(), anyLong(), any());
        System.out.println("[VERIFY] updateBalance/insertWalletHistory 모두 호출되지 않음");
    }
}