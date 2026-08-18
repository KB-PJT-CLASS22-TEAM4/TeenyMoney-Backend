package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.request.SavingPaymentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.SavingPaymentResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FreeSavingPaymentVO;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FreeSavingPaymentServiceTest {
    private static final String KEY = "550e8400-e29b-41d4-a716-446655440000";
    private FinancialProductMapper mapper;
    private WalletMapper walletMapper;
    private TransferService transferService;
    private FreeSavingPaymentService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        walletMapper = mock(WalletMapper.class);
        transferService = mock(TransferService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-14T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new FreeSavingPaymentService(
                mapper, walletMapper, transferService, clock);
    }

    @Test
    @DisplayName("자유적금 납입은 최초 예정일 기준 회차로 PAID 이력을 저장한다")
    void paysFreeSavingWithExistingTransferSystem() {
        FreeSavingPaymentVO saving = activeFreeSaving();
        when(mapper.selectFreeSavingForPaymentForUpdate(2L, 7L)).thenReturn(saving);
        when(mapper.selectFreeSavingPaidAmountInMonth(7L, java.time.LocalDate.of(2026, 8, 14)))
                .thenReturn(20_000L);
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(10L, 100_000L));
        when(walletMapper.selectWalletForUpdate(20L)).thenReturn(wallet(20L, 50_000L));
        TransferVO transfer = new TransferVO();
        transfer.setId(30L);
        when(transferService.transferInExistingTransaction(
                10L, 20L, 30_000L, TransferType.SAVING, KEY)).thenReturn(transfer);

        SavingPaymentResponseDTO response = service.pay(
                new MemberPrincipal(2L, "CHILD"), 7L,
                new SavingPaymentRequestDTO(30_000L, KEY));

        assertThat(response.transferId()).isEqualTo(30L);
        assertThat(response.accumulatedAmount()).isEqualTo(50_000L);
        verify(mapper).insertFreeSavingPayment(7L, 30L, 1, 30_000L);
    }

    @Test
    @DisplayName("같은 UUID로 다시 납입하면 송금하지 않고 최초 성공 결과를 반환한다")
    void sameIdempotencyKeyReturnsExistingPayment() {
        when(mapper.selectFreeSavingForPaymentForUpdate(2L, 7L))
                .thenReturn(activeFreeSaving());
        FreeSavingPaymentVO existing = new FreeSavingPaymentVO();
        existing.setTransferId(30L);
        existing.setPaidAmount(30_000L);
        when(mapper.selectFreeSavingPaymentByIdempotencyKey(7L, KEY))
                .thenReturn(existing);
        when(walletMapper.selectWalletForUpdate(20L)).thenReturn(wallet(20L, 50_000L));

        SavingPaymentResponseDTO response = service.pay(
                new MemberPrincipal(2L, "CHILD"), 7L,
                new SavingPaymentRequestDTO(30_000L, KEY));

        assertThat(response.transferId()).isEqualTo(30L);
        verifyNoInteractions(transferService);
        verify(mapper, never()).insertFreeSavingPayment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("같은 UUID를 다른 납입 금액으로 재사용하면 멱등성 충돌로 거절한다")
    void sameIdempotencyKeyWithDifferentAmountIsRejected() {
        when(mapper.selectFreeSavingForPaymentForUpdate(2L, 7L))
                .thenReturn(activeFreeSaving());
        FreeSavingPaymentVO existing = new FreeSavingPaymentVO();
        existing.setTransferId(30L);
        existing.setPaidAmount(30_000L);
        when(mapper.selectFreeSavingPaymentByIdempotencyKey(7L, KEY))
                .thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.pay(new MemberPrincipal(2L, "CHILD"), 7L,
                        new SavingPaymentRequestDTO(40_000L, KEY)));

        assertThat(exception.getErrorCode()).isEqualTo(
                com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode
                        .IDEMPOTENCY_KEY_CONFLICT);
        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("해당 월 누적 납입액이 최대 한도를 넘으면 송금하지 않는다")
    void rejectsMonthlyLimitExceeded() {
        when(mapper.selectFreeSavingForPaymentForUpdate(2L, 7L))
                .thenReturn(activeFreeSaving());
        when(mapper.selectFreeSavingPaidAmountInMonth(7L, java.time.LocalDate.of(2026, 8, 14)))
                .thenReturn(90_000L);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.pay(new MemberPrincipal(2L, "CHILD"), 7L,
                        new SavingPaymentRequestDTO(20_000L, KEY)));

        assertThat(exception.getErrorCode()).isEqualTo(
                FinancialProductErrorCode.FINANCIAL_PRODUCT_SAVING_MONTHLY_LIMIT_EXCEEDED);
        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("정기적금 가입에는 자유적금 직접납입을 허용하지 않는다")
    void rejectsFixedSaving() {
        FreeSavingPaymentVO saving = activeFreeSaving();
        saving.setSavingsType("FIXED");
        when(mapper.selectFreeSavingForPaymentForUpdate(2L, 7L)).thenReturn(saving);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.pay(new MemberPrincipal(2L, "CHILD"), 7L,
                        new SavingPaymentRequestDTO(30_000L, KEY)));

        assertThat(exception.getErrorCode()).isEqualTo(
                FinancialProductErrorCode.FINANCIAL_PRODUCT_SAVING_NOT_FREE);
        verifyNoInteractions(transferService);
    }

    private FreeSavingPaymentVO activeFreeSaving() {
        FreeSavingPaymentVO saving = new FreeSavingPaymentVO();
        saving.setEnrollmentId(7L);
        saving.setChildId(2L);
        saving.setProductWalletId(20L);
        saving.setSavingsType("FREE");
        saving.setStatus("ACTIVE");
        saving.setMaxMonthAmount(100_000L);
        saving.setPaymentDay(25);
        saving.setStartDate(java.time.LocalDate.of(2026, 8, 25));
        saving.setMaturityDate(java.time.LocalDate.of(2026, 11, 25));
        return saving;
    }

    private WalletVO wallet(Long id, Long balance) {
        WalletVO wallet = new WalletVO();
        wallet.setId(id);
        wallet.setBalance(balance);
        return wallet;
    }
}
