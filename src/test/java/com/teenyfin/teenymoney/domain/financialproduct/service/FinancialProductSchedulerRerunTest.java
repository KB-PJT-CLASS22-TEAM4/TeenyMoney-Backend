package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductMaturityVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FreeSavingMonthlyVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingContributionVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 같은 기준일·기준월로 배치를 재실행했을 때 외부 효과가 한 번만 남는지 묶어서 검증한다. */
class FinancialProductSchedulerRerunTest {

    @Test
    @DisplayName("예금 만기 배치를 같은 날짜로 재실행해도 원금·이자 송금과 점수는 한 번만 반영한다")
    void depositMaturityRerunIsIdempotent() {
        FinancialProductMapper mapper = mock(FinancialProductMapper.class);
        WalletMapper walletMapper = mock(WalletMapper.class);
        TransferService transferService = mock(TransferService.class);
        TeenyScoreChangeService scoreService = mock(TeenyScoreChangeService.class);
        FinancialProductMaturityVO maturity = depositMaturity();
        LocalDate date = LocalDate.of(2027, 1, 1);
        when(mapper.selectDepositMaturityForUpdate(7L, date))
                .thenReturn(maturity, (FinancialProductMaturityVO) null);
        when(walletMapper.selectWalletForUpdate(20L)).thenReturn(wallet(20L, 100_000L));
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(10L, 0L));
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(11L, 1_000_000L));
        when(mapper.markDepositMatured(7L)).thenReturn(1);
        FinancialProductMaturityProcessor processor = new FinancialProductMaturityProcessor(
                mapper, walletMapper, transferService,
                new TeenyScorePolicyService(), scoreService,
                new FinancialProductInterestCalculator(),
                mock(NotificationService.class), memberMapper(),
                mock(TeenyScoreMapper.class));

        processor.processDeposit(7L, date);
        processor.processDeposit(7L, date);

        verify(transferService, times(1)).transferInExistingTransaction(
                20L, 10L, 100_000L, TransferType.DEPOSIT, "DPT_MAT:7:P");
        verify(transferService, times(1)).transferInExistingTransaction(
                eq(11L), eq(10L), longThat(value -> value > 0),
                eq(TransferType.DEPOSIT), eq("DPT_MAT:7:I"));
        verify(scoreService, times(1)).change(any());
        verify(mapper, times(1)).markDepositMatured(7L);
    }

    @Test
    @DisplayName("적금 만기 배치를 같은 날짜로 재실행해도 원금·이자 송금과 점수는 한 번만 반영한다")
    void savingMaturityRerunIsIdempotent() {
        FinancialProductMapper mapper = mock(FinancialProductMapper.class);
        WalletMapper walletMapper = mock(WalletMapper.class);
        TransferService transferService = mock(TransferService.class);
        TeenyScoreChangeService scoreService = mock(TeenyScoreChangeService.class);
        FinancialProductMaturityVO maturity = savingMaturity();
        LocalDate date = LocalDate.of(2027, 1, 1);
        when(mapper.selectSavingMaturityForUpdate(8L, date))
                .thenReturn(maturity, (FinancialProductMaturityVO) null);
        when(walletMapper.selectWalletForUpdate(21L)).thenReturn(wallet(21L, 10_000L));
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(10L, 0L));
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(11L, 1_000_000L));
        when(mapper.selectSavingContributions(8L)).thenReturn(List.of(
                contribution(10_000L, LocalDateTime.of(2026, 1, 1, 0, 0))));
        when(mapper.selectFirstMissedSavingInstallment(8L))
                .thenReturn((Integer) null);
        when(mapper.markSavingMatured(8L)).thenReturn(1);
        FinancialProductMaturityProcessor processor = new FinancialProductMaturityProcessor(
                mapper, walletMapper, transferService,
                new TeenyScorePolicyService(), scoreService,
                new FinancialProductInterestCalculator(),
                mock(NotificationService.class), memberMapper(),
                mock(TeenyScoreMapper.class));

        processor.processSaving(8L, date);
        processor.processSaving(8L, date);

        verify(transferService, times(1)).transferInExistingTransaction(
                21L, 10L, 10_000L, TransferType.SAVING, "SVG_MAT:8:P");
        verify(transferService, times(1)).transferInExistingTransaction(
                eq(11L), eq(10L), longThat(value -> value > 0),
                eq(TransferType.SAVING), eq("SVG_MAT:8:I"));
        verify(scoreService, times(1)).change(any());
        verify(mapper, times(1)).markSavingMatured(8L);
    }

    @Test
    @DisplayName("부모 지갑 잔액이 부족해 이자를 못 보내면 정산 전체를 중단하고 실패 원인을 담아 던진다")
    void depositMaturityFailsWithCauseWhenParentBalanceInsufficient() {
        FinancialProductMapper mapper = mock(FinancialProductMapper.class);
        WalletMapper walletMapper = mock(WalletMapper.class);
        TransferService transferService = mock(TransferService.class);
        TeenyScoreChangeService scoreService = mock(TeenyScoreChangeService.class);
        NotificationService notificationService = mock(NotificationService.class);
        FinancialProductMaturityVO maturity = depositMaturity();
        LocalDate date = LocalDate.of(2027, 1, 1);
        when(mapper.selectDepositMaturityForUpdate(7L, date)).thenReturn(maturity);
        when(walletMapper.selectWalletForUpdate(20L)).thenReturn(wallet(20L, 100_000L));
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(10L, 0L));
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(11L, 0L));
        // 부모(1L) 지갑에서 자녀(10L)로 나가는 이자 송금만 잔액 부족으로 거절되도록 스텁한다.
        when(transferService.transferInExistingTransaction(
                eq(11L), eq(10L), longThat(amount -> amount > 0),
                eq(TransferType.DEPOSIT), eq("DPT_MAT:7:I")))
                .thenThrow(new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE));
        FinancialProductMaturityProcessor processor = new FinancialProductMaturityProcessor(
                mapper, walletMapper, transferService,
                new TeenyScorePolicyService(), scoreService,
                new FinancialProductInterestCalculator(),
                notificationService, memberMapper(),
                mock(TeenyScoreMapper.class));

        FinancialProductInterestPaymentFailedException failure = assertThrows(
                FinancialProductInterestPaymentFailedException.class,
                () -> processor.processDeposit(7L, date));

        assertThat(failure.getEnrollmentId()).isEqualTo(7L);
        assertThat(failure.getChildId()).isEqualTo(2L);
        assertThat(failure.getParentId()).isEqualTo(1L);
        assertThat(failure.getInterest()).isPositive();
        // 이자 송금이 막혔으므로 만기 상태 변경과 알림은 이 트랜잭션 안에서 아예 일어나지 않는다.
        verify(mapper, never()).markDepositMatured(anyLong());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("이자 지급 실패 알림은 부모에게만 별도 트랜잭션에서 발송한다")
    void notifyInterestPaymentFailedSendsParentOnlyNotification() {
        NotificationService notificationService = mock(NotificationService.class);
        FinancialProductMaturityProcessor processor = new FinancialProductMaturityProcessor(
                mock(FinancialProductMapper.class), mock(WalletMapper.class),
                mock(TransferService.class), new TeenyScorePolicyService(),
                mock(TeenyScoreChangeService.class), new FinancialProductInterestCalculator(),
                notificationService, mock(MemberMapper.class),
                mock(TeenyScoreMapper.class));
        FinancialProductInterestPaymentFailedException failure =
                new FinancialProductInterestPaymentFailedException(
                        7L, 2L, 1L, "테스트 예금", FinancialProductType.DEPOSIT, 3_000L,
                        new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE));

        processor.notifyInterestPaymentFailed(failure);

        verify(notificationService).createNotification(
                eq(1L), anyString(), anyString(),
                eq(NotificationReferenceType.DEPOSIT_MATURITY), eq(7L), eq(true));
    }

    @Test
    @DisplayName("자유적금 월 점수 배치를 같은 월로 재실행해도 점수와 이력은 한 번만 반영한다")
    void freeSavingMonthlyScoreRerunIsIdempotent() {
        FinancialProductMapper mapper = mock(FinancialProductMapper.class);
        TeenyScoreMapper scoreMapper = mock(TeenyScoreMapper.class);
        YearMonth month = YearMonth.of(2026, 8);
        FreeSavingMonthlyVO saving = new FreeSavingMonthlyVO();
        saving.setEnrollmentId(9L);
        saving.setChildId(2L);
        saving.setMonthlyAmount(10_000L);
        when(mapper.selectFreeSavingMonthlyForUpdate(
                9L, month.atDay(1), month.plusMonths(1).atDay(1)))
                .thenReturn(saving);
        when(mapper.selectSavingPaidAmountBetween(anyLong(), any(), any()))
                .thenReturn(10_000L);
        when(scoreMapper.selectScoreForUpdate(2L)).thenReturn(600);
        when(scoreMapper.existsHistoryByEventKey(
                2L, "SAVING_FREE_MONTHLY:9:2026-08"))
                .thenReturn(false, true);
        FreeSavingMonthlyScoreProcessor processor = new FreeSavingMonthlyScoreProcessor(
                mapper, new TeenyScorePolicyService(),
                new TeenyScoreChangeService(scoreMapper));

        processor.process(9L, month);
        processor.process(9L, month);

        verify(scoreMapper, times(1)).updateTeenyScore(2L, 608);
        verify(scoreMapper, times(1)).insertScoreHistory(
                eq(2L), eq(8), eq(608), eq("SAVING_FREE_MONTHLY_RESULT"),
                eq("SAVING_FREE_MONTHLY:9:2026-08"), anyString(),
                eq("SAVING_ENROLLMENT"), eq(9L));
    }

    private FinancialProductMaturityVO depositMaturity() {
        FinancialProductMaturityVO value = baseMaturity(7L, 20L);
        value.setAppliedRate(new BigDecimal("3.00"));
        return value;
    }

    private FinancialProductMaturityVO savingMaturity() {
        FinancialProductMaturityVO value = baseMaturity(8L, 21L);
        value.setAppliedRate(new BigDecimal("3.00"));
        value.setMonthlyAmount(10_000L);
        value.setSavingsType("FIXED");
        return value;
    }

    private FinancialProductMaturityVO baseMaturity(long id, long walletId) {
        FinancialProductMaturityVO value = new FinancialProductMaturityVO();
        value.setEnrollmentId(id);
        value.setChildId(2L);
        value.setParentId(1L);
        value.setProductWalletId(walletId);
        value.setProductName("테스트 상품");
        value.setTermMonths(12);
        value.setInterestCalculationType("SIMPLE");
        value.setStartDate(LocalDate.of(2026, 1, 1));
        value.setMaturityDate(LocalDate.of(2027, 1, 1));
        value.setStatus("ACTIVE");
        return value;
    }

    private SavingContributionVO contribution(long amount, LocalDateTime paidAt) {
        SavingContributionVO value = new SavingContributionVO();
        value.setPaidAmount(amount);
        value.setPaidAt(paidAt);
        return value;
    }

    /** 만기 알림이 자녀 이름을 조회하므로 회원 조회만 최소한으로 대신한다. */
    private MemberMapper memberMapper() {
        MemberMapper memberMapper = mock(MemberMapper.class);
        MemberVO child = new MemberVO();
        child.setName("테스트자녀");
        when(memberMapper.selectById(2L)).thenReturn(child);
        return memberMapper;
    }

    private WalletVO wallet(long id, long balance) {
        WalletVO wallet = new WalletVO();
        wallet.setId(id);
        wallet.setBalance(balance);
        return wallet;
    }
}
