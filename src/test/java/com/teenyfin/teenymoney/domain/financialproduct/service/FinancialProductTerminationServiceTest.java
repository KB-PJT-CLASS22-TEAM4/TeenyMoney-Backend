package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductTerminationResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductTerminationVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingContributionVO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinancialProductTerminationServiceTest {
    private FinancialProductMapper mapper;
    private WalletMapper walletMapper;
    private TransferService transferService;
    private TeenyScoreChangeService scoreChangeService;
    private FinancialProductTerminationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        walletMapper = mock(WalletMapper.class);
        transferService = mock(TransferService.class);
        scoreChangeService = mock(TeenyScoreChangeService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new FinancialProductTerminationService(
                mapper, walletMapper, transferService,
                new TeenyScorePolicyService(), scoreChangeService,
                new EarlyTerminationRatePolicy(),
                new FinancialProductInterestCalculator(), clock);
    }

    @Test
    @DisplayName("예금 중도해지 예상 조회는 금액과 점수만 계산하고 송금하거나 상태를 변경하지 않는다")
    void depositQuoteHasNoSideEffects() {
        when(mapper.selectDepositTermination(2L, 7L)).thenReturn(activeDeposit());

        FinancialProductTerminationResponseDTO result = service.quote(
                new MemberPrincipal(2L, "CHILD"), "deposit", 7L);

        assertThat(result.getPrincipalAmount()).isEqualTo(100_000L);
        assertThat(result.getInterestAmount()).isPositive();
        assertThat(result.getFinalAmount())
                .isEqualTo(result.getPrincipalAmount() + result.getInterestAmount());
        assertThat(result.isTerminated()).isFalse();
        verifyNoInteractions(transferService, scoreChangeService);
        verify(walletMapper, never()).selectWalletForUpdate(anyLong());
        verify(mapper, never()).markDepositTerminated(any());
    }

    @Test
    @DisplayName("예금 중도해지는 원금과 이자를 구분 송금하고 점수와 계약 상태를 한 번 변경한다")
    void depositTerminationSettlesPrincipalInterestAndScore() {
        when(mapper.selectDepositTerminationForUpdate(2L, 7L))
                .thenReturn(activeDeposit());
        when(walletMapper.selectWalletForUpdate(20L)).thenReturn(wallet(20L, 100_000L));
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(10L, 0L));
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(11L, 1_000_000L));
        when(mapper.markDepositTerminated(7L)).thenReturn(1);

        FinancialProductTerminationResponseDTO result = service.terminate(
                new MemberPrincipal(2L, "CHILD"), "deposit", 7L);

        assertThat(result.isTerminated()).isTrue();
        verify(transferService).transferInExistingTransaction(
                20L, 10L, 100_000L, TransferType.DEPOSIT, "DPT_TERM:7:P");
        verify(transferService).transferInExistingTransaction(
                eq(11L), eq(10L), longThat(amount -> amount > 0),
                eq(TransferType.DEPOSIT), eq("DPT_TERM:7:I"));
        verify(scoreChangeService).change(any());
        verify(mapper).markDepositTerminated(7L);
    }

    @Test
    @DisplayName("이미 중도해지된 계약을 다시 요청하면 송금과 점수를 중복 반영하지 않는다")
    void repeatedTerminationDoesNotSettleAgain() {
        FinancialProductTerminationVO terminated = activeDeposit();
        terminated.setStatus("TERMINATED");
        when(mapper.selectDepositTerminationForUpdate(2L, 7L)).thenReturn(terminated);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.terminate(new MemberPrincipal(2L, "CHILD"), "deposit", 7L));

        assertThat(exception.getErrorCode()).isEqualTo(
                FinancialProductErrorCode.FINANCIAL_PRODUCT_TERMINATION_NOT_AVAILABLE);
        verifyNoInteractions(transferService, scoreChangeService);
        verify(mapper, never()).markDepositTerminated(any());
    }

    @Test
    @DisplayName("납입 이력이 있는 적금의 중도해지 예상 원금과 이자를 계산한다")
    void savingQuoteUsesPaidContributions() {
        when(mapper.selectSavingTermination(2L, 8L)).thenReturn(activeSaving());
        when(mapper.selectSavingContributions(8L)).thenReturn(savingContributions());

        FinancialProductTerminationResponseDTO result = service.quote(
                new MemberPrincipal(2L, "CHILD"), "saving", 8L);

        assertThat(result.getProductType()).isEqualTo("SAVING");
        assertThat(result.getPrincipalAmount()).isEqualTo(30_000L);
        assertThat(result.getInterestAmount()).isPositive();
        assertThat(result.getFinalAmount())
                .isEqualTo(result.getPrincipalAmount() + result.getInterestAmount());
        assertThat(result.isTerminated()).isFalse();
        verifyNoInteractions(transferService, scoreChangeService);
    }

    @Test
    @DisplayName("적금 중도해지는 납입 원금과 이자를 각각 자녀 지갑으로 지급한다")
    void savingTerminationPaysPrincipalAndInterest() {
        stubActiveSavingTermination();

        FinancialProductTerminationResponseDTO result = service.terminate(
                new MemberPrincipal(2L, "CHILD"), "saving", 8L);

        verify(transferService).transferInExistingTransaction(
                21L, 10L, 30_000L, TransferType.SAVING, "SVG_TERM:8:P");
        verify(transferService).transferInExistingTransaction(
                eq(11L), eq(10L), eq(result.getInterestAmount()),
                eq(TransferType.SAVING), eq("SVG_TERM:8:I"));
        assertThat(result.getInterestAmount()).isPositive();
    }

    @Test
    @DisplayName("적금 중도해지 정산이 완료되면 계약 상태를 TERMINATED로 변경한다")
    void savingTerminationMarksEnrollmentTerminated() {
        stubActiveSavingTermination();

        FinancialProductTerminationResponseDTO result = service.terminate(
                new MemberPrincipal(2L, "CHILD"), "saving", 8L);

        assertThat(result.isTerminated()).isTrue();
        verify(scoreChangeService).change(any());
        verify(mapper).markSavingTerminated(8L);
    }

    @Test
    @DisplayName("이미 중도해지된 적금을 다시 요청하면 송금과 점수를 중복 반영하지 않는다")
    void repeatedSavingTerminationDoesNotSettleAgain() {
        FinancialProductTerminationVO terminated = activeSaving();
        terminated.setStatus("TERMINATED");
        when(mapper.selectSavingTerminationForUpdate(2L, 8L)).thenReturn(terminated);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.terminate(new MemberPrincipal(2L, "CHILD"), "saving", 8L));

        assertThat(exception.getErrorCode()).isEqualTo(
                FinancialProductErrorCode.FINANCIAL_PRODUCT_TERMINATION_NOT_AVAILABLE);
        verifyNoInteractions(transferService, scoreChangeService);
        verify(mapper, never()).markSavingTerminated(any());
    }

    @Test
    @DisplayName("적금 납입 이력 합계와 상품 지갑 잔액이 다르면 중도해지 정산을 중단한다")
    void savingTerminationStopsWhenHistoryAndWalletDiffer() {
        when(mapper.selectSavingTerminationForUpdate(2L, 8L))
                .thenReturn(activeSaving());
        when(walletMapper.selectWalletForUpdate(21L)).thenReturn(wallet(21L, 29_000L));
        when(mapper.selectSavingContributions(8L)).thenReturn(savingContributions());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.terminate(new MemberPrincipal(2L, "CHILD"), "saving", 8L));

        assertThat(exception.getMessage()).contains("납입 이력과 상품 지갑 잔액");
        verifyNoInteractions(transferService, scoreChangeService);
        verify(mapper, never()).markSavingTerminated(any());
    }

    private FinancialProductTerminationVO activeDeposit() {
        FinancialProductTerminationVO enrollment = new FinancialProductTerminationVO();
        enrollment.setEnrollmentId(7L);
        enrollment.setChildId(2L);
        enrollment.setParentId(1L);
        enrollment.setProductWalletId(20L);
        enrollment.setProductWalletBalance(100_000L);
        enrollment.setAppliedEarlyTerminationRate(new BigDecimal("1.00"));
        enrollment.setTermMonths(12);
        enrollment.setInterestCalculationType("SIMPLE");
        enrollment.setStartDate(LocalDate.of(2026, 1, 1));
        enrollment.setMaturityDate(LocalDate.of(2027, 1, 1));
        enrollment.setStatus("ACTIVE");
        return enrollment;
    }

    private FinancialProductTerminationVO activeSaving() {
        FinancialProductTerminationVO enrollment = new FinancialProductTerminationVO();
        enrollment.setEnrollmentId(8L);
        enrollment.setChildId(2L);
        enrollment.setParentId(1L);
        enrollment.setProductWalletId(21L);
        enrollment.setProductWalletBalance(30_000L);
        enrollment.setAppliedEarlyTerminationRate(new BigDecimal("3.00"));
        enrollment.setTermMonths(12);
        enrollment.setSavingsType("FIXED");
        enrollment.setInterestCalculationType("SIMPLE");
        enrollment.setStartDate(LocalDate.of(2026, 1, 1));
        enrollment.setMaturityDate(LocalDate.of(2027, 1, 1));
        enrollment.setStatus("ACTIVE");
        return enrollment;
    }

    private List<SavingContributionVO> savingContributions() {
        return List.of(
                contribution(10_000L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                contribution(20_000L, LocalDateTime.of(2026, 4, 1, 0, 0)));
    }

    private SavingContributionVO contribution(long amount, LocalDateTime paidAt) {
        SavingContributionVO contribution = new SavingContributionVO();
        contribution.setPaidAmount(amount);
        contribution.setPaidAt(paidAt);
        return contribution;
    }

    private void stubActiveSavingTermination() {
        when(mapper.selectSavingTerminationForUpdate(2L, 8L))
                .thenReturn(activeSaving());
        when(walletMapper.selectWalletForUpdate(21L)).thenReturn(wallet(21L, 30_000L));
        when(mapper.selectSavingContributions(8L)).thenReturn(savingContributions());
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(10L, 0L));
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(11L, 1_000_000L));
        when(mapper.markSavingTerminated(8L)).thenReturn(1);
    }

    private WalletVO wallet(Long id, long balance) {
        WalletVO wallet = new WalletVO();
        wallet.setId(id);
        wallet.setBalance(balance);
        return wallet;
    }
}
