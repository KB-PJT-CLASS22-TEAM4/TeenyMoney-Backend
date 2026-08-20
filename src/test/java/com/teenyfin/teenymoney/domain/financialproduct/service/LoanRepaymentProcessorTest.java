package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanRepaymentVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoanRepaymentProcessorTest {
    private FinancialProductMapper mapper;
    private WalletMapper walletMapper;
    private TransferService transferService;
    private TeenyScoreChangeService scoreChangeService;
    private NotificationService notificationService;
    private LoanRepaymentProcessor processor;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        walletMapper = mock(WalletMapper.class);
        transferService = mock(TransferService.class);
        scoreChangeService = mock(TeenyScoreChangeService.class);
        notificationService = mock(NotificationService.class);
        processor = new LoanRepaymentProcessor(
                mapper, walletMapper, transferService,
                new TeenyScorePolicyService(), scoreChangeService,
                new LoanRepaymentCalculator(), notificationService);
    }

    @Test
    @DisplayName("상환일에 잔액이 충분하면 회차 원금과 이자를 부모 지갑으로 전액 자동상환한다")
    void fullPaymentKeepsLoanActive() {
        stubLoan(activeLoan(120_000L, 12), 10_700L);

        processor.process(7L, LocalDate.of(2026, 1, 15));

        verify(transferService).transferInExistingTransaction(
                10L, 11L, 10_700L, TransferType.LOAN, "LOAN:7:1");
        verify(mapper).insertLoanRepaymentHistory(
                7L, 30L, 1, "SCHEDULED", 10_000L, 10_000L,
                700L, 700L, "PAID", LocalDate.of(2026, 1, 15));
        verify(mapper).updateLoanAfterRepayment(7L, 110_000L, 0L, 1, "ACTIVE");
        verifyNoInteractions(scoreChangeService);
    }

    @Test
    @DisplayName("자녀 잔액이 청구액보다 적으면 가능한 금액만 상환하고 PARTIAL 및 OVERDUE로 기록한다")
    void partialPaymentMarksLoanOverdue() {
        stubLoan(activeLoan(120_000L, 12), 500L);

        processor.process(7L, LocalDate.of(2026, 1, 15));

        verify(transferService).transferInExistingTransaction(
                10L, 11L, 500L, TransferType.LOAN, "LOAN:7:1");
        verify(mapper).insertLoanRepaymentHistory(
                7L, 30L, 1, "SCHEDULED", 10_000L, 0L,
                700L, 500L, "PARTIAL", LocalDate.of(2026, 1, 15));
        verify(mapper).updateLoanAfterRepayment(7L, 120_000L, 200L, 0, "OVERDUE");
        verify(scoreChangeService).change(any());
    }

    @Test
    @DisplayName("자녀 지갑 잔액이 0원이면 송금 없이 OVERDUE 이력과 연체 점수를 남긴다")
    void zeroBalanceCreatesOverdueWithoutTransfer() {
        stubLoan(activeLoan(120_000L, 12), 0L);

        processor.process(7L, LocalDate.of(2026, 1, 15));

        verifyNoInteractions(transferService);
        verify(mapper).insertLoanRepaymentHistory(
                7L, null, 1, "SCHEDULED", 10_000L, 0L,
                700L, 0L, "OVERDUE", LocalDate.of(2026, 1, 15));
        verify(mapper).updateLoanAfterRepayment(7L, 120_000L, 700L, 0, "OVERDUE");
        verify(scoreChangeService).change(any());
        // 상환은 자녀 지갑에서 나가는 돈이므로 부모가 아닌 자녀에게만 알린다.
        verify(notificationService).createNotification(
                eq(2L), eq("대출 상환이 연체됐어요"), anyString(),
                eq(NotificationReferenceType.LOAN_REPAYMENT), eq(7L), eq(true));
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    @DisplayName("마지막 회차에 남은 원금과 이자를 모두 상환하면 대출을 REPAID로 완납 처리한다")
    void lastInstallmentMarksLoanRepaid() {
        LoanRepaymentVO loan = activeLoan(10_000L, 1);
        loan.setPrincipalAmount(10_000L);
        stubLoan(loan, 10_058L);

        processor.process(7L, LocalDate.of(2026, 1, 15));

        verify(mapper).updateLoanAfterRepayment(7L, 0L, 0L, 1, "REPAID");
        verify(scoreChangeService).change(argThat(request ->
                "LOAN_REPAID:7".equals(request.getEventKey())));
        verify(notificationService).createNotification(
                eq(2L), eq("대출을 모두 갚았어요"), anyString(),
                eq(NotificationReferenceType.LOAN_REPAYMENT), eq(7L), eq(true));
    }

    @Test
    @DisplayName("동일 회차 이력이 있으면 스케줄러를 재실행해도 송금과 점수를 중복 반영하지 않는다")
    void existingInstallmentPreventsDuplicateProcessing() {
        when(mapper.selectLoanRepaymentForUpdate(7L)).thenReturn(activeLoan(120_000L, 12));
        when(mapper.countLoanRepaymentHistory(7L, 1)).thenReturn(1);

        processor.process(7L, LocalDate.of(2026, 1, 15));

        verifyNoInteractions(transferService, walletMapper, scoreChangeService);
        verify(mapper, never()).insertLoanRepaymentHistory(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(mapper, never()).updateLoanAfterRepayment(
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("이전 회차 미납 원금은 다음 회차 원금과 합산하여 자동상환한다")
    void nextInstallmentCollectsPreviousUnpaidPrincipal() {
        LoanRepaymentVO loan = activeLoan(120_000L, 12);
        when(mapper.selectLoanRepaymentForUpdate(7L)).thenReturn(loan);
        when(mapper.countLoanRepaymentHistory(7L, 1)).thenReturn(1);
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(10L, 20_767L));
        when(walletMapper.selectWalletForUpdate(10L)).thenReturn(wallet(10L, 20_767L));
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(11L, 0L));
        TransferVO transfer = new TransferVO();
        transfer.setId(30L);
        when(transferService.transferInExistingTransaction(
                anyLong(), anyLong(), anyLong(), eq(TransferType.LOAN), anyString()))
                .thenReturn(transfer);
        when(mapper.updateLoanAfterRepayment(
                anyLong(), anyLong(), anyLong(), anyInt(), anyString())).thenReturn(1);

        processor.process(7L, LocalDate.of(2026, 2, 15));

        verify(transferService).transferInExistingTransaction(
                10L, 11L, 20_767L, TransferType.LOAN, "LOAN:7:2");
        verify(mapper).insertLoanRepaymentHistory(
                7L, 30L, 2, "SCHEDULED", 20_000L, 20_000L,
                767L, 767L, "PAID", LocalDate.of(2026, 2, 15));
        verify(mapper).updateLoanAfterRepayment(7L, 100_000L, 0L, 1, "ACTIVE");
    }

    @Test
    @DisplayName("조기상환으로 원금이 미리 줄어든 상태에서도 다음 정규 회차를 정상 계산한다")
    void nextInstallmentReflectsPriorEarlyRepayment() {
        LoanRepaymentVO loan = activeLoan(120_000L, 12);
        // 1회차는 이미 처리됐고, 그 사이 조기상환으로 원금이 예정보다 훨씬 낮은 60,000원까지 줄었다고 가정한다.
        loan.setOutstandingPrincipal(60_000L);
        when(mapper.selectLoanRepaymentForUpdate(7L)).thenReturn(loan);
        when(mapper.countLoanRepaymentHistory(7L, 1)).thenReturn(1);
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(10L, 20_000L));
        when(walletMapper.selectWalletForUpdate(10L)).thenReturn(wallet(10L, 20_000L));
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(11L, 0L));
        TransferVO transfer = new TransferVO();
        transfer.setId(30L);
        when(transferService.transferInExistingTransaction(
                anyLong(), anyLong(), anyLong(), eq(TransferType.LOAN), anyString()))
                .thenReturn(transfer);
        when(mapper.updateLoanAfterRepayment(
                anyLong(), anyLong(), anyLong(), anyInt(), anyString())).thenReturn(1);

        processor.process(7L, LocalDate.of(2026, 2, 15));

        // 실제 잔액(60,000)이 정상 스케줄상 잔액(110,000)보다 낮아도 미납으로 오인하지 않고
        // 이번 회차 약정 원금(10,000)만 정상 청구한다.
        verify(transferService).transferInExistingTransaction(
                10L, 11L, 10_350L, TransferType.LOAN, "LOAN:7:2");
        verify(mapper).insertLoanRepaymentHistory(
                7L, 30L, 2, "SCHEDULED", 10_000L, 10_000L,
                350L, 350L, "PAID", LocalDate.of(2026, 2, 15));
        verify(mapper).updateLoanAfterRepayment(7L, 50_000L, 0L, 1, "ACTIVE");
    }

    @Test
    @DisplayName("만기일까지 미완납이면 DEFAULTED로 변경하고 기본 감점을 한 번 반영한다")
    void unpaidAtMaturityBecomesDefaulted() {
        LoanRepaymentVO loan = activeLoan(10_000L, 1);
        loan.setPrincipalAmount(10_000L);
        loan.setMaturityDate(LocalDate.of(2026, 1, 15));
        stubLoan(loan, 0L);
        when(mapper.countLoanRepaymentHistoryOnDate(
                7L, 1, LocalDate.of(2026, 1, 15))).thenReturn(1);

        processor.process(7L, LocalDate.of(2026, 1, 15));

        verify(mapper).updateLoanAfterRepayment(7L, 10_000L, 58L, 0, "DEFAULTED");
        verify(scoreChangeService).change(argThat(request ->
                "LOAN_DEFAULTED:7".equals(request.getEventKey())));
    }

    @Test
    @DisplayName("만기 후 자녀 지갑에 잔액이 생기면 연체이자부터 상환하고 REPAID로 변경한다")
    void postMaturityPaymentCanFinishDefaultedLoan() {
        LoanRepaymentVO loan = activeLoan(10_000L, 1);
        loan.setPrincipalAmount(10_000L);
        loan.setOverdueInterest(100L);
        loan.setStatus("DEFAULTED");
        loan.setMaturityDate(LocalDate.of(2026, 1, 15));
        stubLoan(loan, 10_102L);
        when(mapper.countLoanRepaymentHistory(7L, 1)).thenReturn(1);
        when(mapper.countLoanRepaymentHistoryOnDate(
                7L, 1, LocalDate.of(2026, 1, 16))).thenReturn(0);
        when(mapper.selectLastLoanRepaymentAttemptDate(7L, 1))
                .thenReturn(LocalDate.of(2026, 1, 15));

        processor.process(7L, LocalDate.of(2026, 1, 16));

        verify(transferService).transferInExistingTransaction(
                10L, 11L, 10_102L, TransferType.LOAN,
                "LOAN:OVERDUE:7:2026-01-16");
        verify(mapper).insertLoanRepaymentHistory(
                7L, 30L, 1, "SCHEDULED", 10_000L, 10_000L,
                102L, 102L, "PAID", LocalDate.of(2026, 1, 16));
        verify(mapper).updateLoanAfterRepayment(7L, 0L, 0L, 0, "REPAID");
        verify(scoreChangeService).change(argThat(request ->
                "LOAN_POST_MATURITY_OVERDUE:7:2026-01".equals(request.getEventKey())));
    }

    @Test
    @DisplayName("만기 후 같은 날짜에 배치를 재실행해도 상환 시도를 중복 생성하지 않는다")
    void postMaturityAttemptRunsOncePerDay() {
        LoanRepaymentVO loan = activeLoan(10_000L, 1);
        loan.setStatus("DEFAULTED");
        loan.setMaturityDate(LocalDate.of(2026, 1, 15));
        when(mapper.selectLoanRepaymentForUpdate(7L)).thenReturn(loan);
        when(mapper.countLoanRepaymentHistory(7L, 1)).thenReturn(1);
        when(mapper.countLoanRepaymentHistoryOnDate(
                7L, 1, LocalDate.of(2026, 1, 16))).thenReturn(1);

        processor.process(7L, LocalDate.of(2026, 1, 16));

        verifyNoInteractions(walletMapper, transferService);
        verify(mapper, never()).insertLoanRepaymentHistory(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(mapper, never()).updateLoanAfterRepayment(
                any(), any(), any(), any(), any());
    }

    private void stubLoan(LoanRepaymentVO loan, long childBalance) {
        when(mapper.selectLoanRepaymentForUpdate(7L)).thenReturn(loan);
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(10L, childBalance));
        when(walletMapper.selectWalletForUpdate(10L)).thenReturn(wallet(10L, childBalance));
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(11L, 0L));
        TransferVO transfer = new TransferVO();
        transfer.setId(30L);
        when(transferService.transferInExistingTransaction(
                anyLong(), anyLong(), anyLong(), eq(TransferType.LOAN), anyString()))
                .thenReturn(transfer);
        when(mapper.updateLoanAfterRepayment(
                anyLong(), anyLong(), anyLong(), anyInt(), anyString())).thenReturn(1);
    }

    private LoanRepaymentVO activeLoan(long outstanding, int termMonths) {
        LoanRepaymentVO loan = new LoanRepaymentVO();
        loan.setEnrollmentId(7L);
        loan.setParentId(1L);
        loan.setChildId(2L);
        loan.setProductName("테스트 대출");
        loan.setPrincipalAmount(120_000L);
        loan.setOutstandingPrincipal(outstanding);
        loan.setOverdueInterest(0L);
        loan.setAppliedRate(new BigDecimal("7.00"));
        loan.setAppliedLateFeeRate(new BigDecimal("8.00"));
        loan.setPaymentDay(15);
        loan.setPaidCount(0);
        loan.setTermMonths(termMonths);
        loan.setStartDate(LocalDate.of(2026, 1, 1));
        loan.setMaturityDate(LocalDate.of(2027, 1, 1));
        loan.setRepaymentType("EQUAL_PRINCIPAL");
        loan.setStatus("ACTIVE");
        return loan;
    }

    private WalletVO wallet(long id, long balance) {
        WalletVO wallet = new WalletVO();
        wallet.setId(id);
        wallet.setBalance(balance);
        return wallet;
    }
}
