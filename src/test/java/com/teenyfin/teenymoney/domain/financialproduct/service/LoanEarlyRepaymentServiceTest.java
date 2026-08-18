package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.request.LoanEarlyRepaymentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.LoanEarlyRepaymentResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanEarlyRepaymentHistoryVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanRepaymentVO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoanEarlyRepaymentServiceTest {
    private static final MemberPrincipal CHILD = new MemberPrincipal(2L, "CHILD");
    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";

    private FinancialProductMapper mapper;
    private WalletMapper walletMapper;
    private TransferService transferService;
    private TeenyScoreChangeService scoreChangeService;
    private LoanEarlyRepaymentService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        walletMapper = mock(WalletMapper.class);
        transferService = mock(TransferService.class);
        scoreChangeService = mock(TeenyScoreChangeService.class);
        service = new LoanEarlyRepaymentService(
                mapper, walletMapper, transferService,
                new TeenyScorePolicyService(), scoreChangeService);
    }

    @Test
    @DisplayName("연체이자가 있으면 요청 금액에서 이자부터 충당하고 남은 만큼 원금을 줄인다")
    void repayPaysOverdueInterestFirst() {
        LoanRepaymentVO loan = activeLoan(100_000L, 1_000L);
        when(mapper.selectLoanEarlyRepaymentByIdempotencyKey(7L, IDEMPOTENCY_KEY))
                .thenReturn(null);
        when(mapper.selectLoanRepaymentForChildForUpdate(2L, 7L)).thenReturn(loan);
        stubWallets(50_000L);
        stubTransfer();
        when(mapper.updateLoanAfterRepayment(
                eq(7L), anyLong(), anyLong(), anyInt(), anyString())).thenReturn(1);

        LoanEarlyRepaymentResponseDTO response = service.repay(
                CHILD, 7L, new LoanEarlyRepaymentRequestDTO(21_000L, IDEMPOTENCY_KEY));

        assertThat(response.getCurrentOutstandingPrincipal()).isEqualTo(100_000L);
        assertThat(response.getCurrentOverdueInterest()).isEqualTo(1_000L);
        assertThat(response.getPaidInterestAmount()).isEqualTo(1_000L);
        assertThat(response.getPaidPrincipalAmount()).isEqualTo(20_000L);
        assertThat(response.getRemainingOutstandingPrincipal()).isEqualTo(80_000L);
        assertThat(response.getRemainingOverdueInterest()).isZero();
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.isExecuted()).isTrue();

        verify(transferService).transferInExistingTransaction(
                10L, 11L, 21_000L, TransferType.LOAN, IDEMPOTENCY_KEY);
        verify(mapper).insertLoanRepaymentHistory(
                7L, 30L, 0, "EARLY", 20_000L, 20_000L,
                1_000L, 1_000L, "PAID", null);
        verify(mapper).updateLoanAfterRepayment(7L, 80_000L, 0L, 0, "ACTIVE");
        verifyNoInteractions(scoreChangeService);
    }

    @Test
    @DisplayName("잔여 원금과 연체이자를 전부 갚으면 대출을 REPAID로 자동 종료하고 점수를 반영한다")
    void repayInFullClosesLoanAndGrantsScore() {
        LoanRepaymentVO loan = activeLoan(10_000L, 0L);
        when(mapper.selectLoanRepaymentForChildForUpdate(2L, 7L)).thenReturn(loan);
        stubWallets(10_000L);
        stubTransfer();
        when(mapper.updateLoanAfterRepayment(
                eq(7L), anyLong(), anyLong(), anyInt(), anyString())).thenReturn(1);

        LoanEarlyRepaymentResponseDTO response = service.repay(
                CHILD, 7L, new LoanEarlyRepaymentRequestDTO(10_000L, IDEMPOTENCY_KEY));

        assertThat(response.getStatus()).isEqualTo("REPAID");
        assertThat(response.getRemainingOutstandingPrincipal()).isZero();
        assertThat(response.getScoreChange()).isEqualTo(6);
        verify(mapper).updateLoanAfterRepayment(7L, 0L, 0L, 0, "REPAID");
        verify(scoreChangeService).change(argThat(request ->
                "LOAN_REPAID:7".equals(request.getEventKey())));
    }

    @Test
    @DisplayName("요청 금액이 남은 원금과 연체이자 합계를 초과하면 거절한다")
    void repayRejectsAmountExceedingTotalDue() {
        LoanRepaymentVO loan = activeLoan(10_000L, 0L);
        when(mapper.selectLoanRepaymentForChildForUpdate(2L, 7L)).thenReturn(loan);

        assertThatThrownBy(() -> service.repay(
                CHILD, 7L, new LoanEarlyRepaymentRequestDTO(10_001L, IDEMPOTENCY_KEY)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", FINANCIAL_PRODUCT_LOAN_EARLY_REPAYMENT_AMOUNT_EXCEEDED);
        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("이미 REPAID된 대출에 조기상환을 요청하면 거절한다")
    void repayRejectsAlreadyRepaidLoan() {
        LoanRepaymentVO loan = activeLoan(0L, 0L);
        loan.setStatus("REPAID");
        when(mapper.selectLoanRepaymentForChildForUpdate(2L, 7L)).thenReturn(loan);

        assertThatThrownBy(() -> service.repay(
                CHILD, 7L, new LoanEarlyRepaymentRequestDTO(1L, IDEMPOTENCY_KEY)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", FINANCIAL_PRODUCT_LOAN_EARLY_REPAYMENT_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("DEFAULTED 상태인 대출도 조기상환으로 갚아 REPAID로 종료할 수 있다")
    void repayAllowsDefaultedLoanToBeClosed() {
        LoanRepaymentVO loan = activeLoan(5_000L, 2_000L);
        loan.setStatus("DEFAULTED");
        when(mapper.selectLoanRepaymentForChildForUpdate(2L, 7L)).thenReturn(loan);
        stubWallets(7_000L);
        stubTransfer();
        when(mapper.updateLoanAfterRepayment(
                eq(7L), anyLong(), anyLong(), anyInt(), anyString())).thenReturn(1);

        LoanEarlyRepaymentResponseDTO response = service.repay(
                CHILD, 7L, new LoanEarlyRepaymentRequestDTO(7_000L, IDEMPOTENCY_KEY));

        assertThat(response.getStatus()).isEqualTo("REPAID");
        verify(mapper).updateLoanAfterRepayment(7L, 0L, 0L, 0, "REPAID");
        verify(scoreChangeService).change(any());
    }

    @Test
    @DisplayName("본인 소유가 아니거나 존재하지 않는 계약이면 찾을 수 없음으로 응답한다")
    void repayRejectsMissingOrUnownedEnrollment() {
        when(mapper.selectLoanRepaymentForChildForUpdate(2L, 7L)).thenReturn(null);

        assertThatThrownBy(() -> service.repay(
                CHILD, 7L, new LoanEarlyRepaymentRequestDTO(1L, IDEMPOTENCY_KEY)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", FINANCIAL_PRODUCT_ENROLLMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("자녀 지갑 잔액이 요청 금액보다 적으면 거절한다")
    void repayRejectsInsufficientBalance() {
        LoanRepaymentVO loan = activeLoan(100_000L, 0L);
        when(mapper.selectLoanRepaymentForChildForUpdate(2L, 7L)).thenReturn(loan);
        stubWallets(5_000L);

        assertThatThrownBy(() -> service.repay(
                CHILD, 7L, new LoanEarlyRepaymentRequestDTO(10_000L, IDEMPOTENCY_KEY)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", WalletErrorCode.INSUFFICIENT_BALANCE);
        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("같은 멱등키로 재요청하면 잔액을 다시 차감하지 않고 최초 결과를 그대로 반환한다")
    void repayIsIdempotentOnRetry() {
        LoanEarlyRepaymentHistoryVO existing = new LoanEarlyRepaymentHistoryVO();
        existing.setTransferId(30L);
        existing.setPaidPrincipalAmount(20_000L);
        existing.setPaidInterestAmount(1_000L);
        when(mapper.selectLoanEarlyRepaymentByIdempotencyKey(7L, IDEMPOTENCY_KEY))
                .thenReturn(existing);
        LoanRepaymentVO current = activeLoan(80_000L, 0L);
        when(mapper.selectLoanRepaymentForRead(2L, 7L)).thenReturn(current);

        LoanEarlyRepaymentResponseDTO response = service.repay(
                CHILD, 7L, new LoanEarlyRepaymentRequestDTO(21_000L, IDEMPOTENCY_KEY));

        // 최초 처리 시점(그때는 아직 80,000/0으로 안 줄어있었을 때) 기준의 "상환 전" 값을
        // 지금 남은 값(80,000/0)에 그때 갚은 만큼(20,000/1,000)을 더해 복원한다.
        assertThat(response.getCurrentOutstandingPrincipal()).isEqualTo(100_000L);
        assertThat(response.getCurrentOverdueInterest()).isEqualTo(1_000L);
        assertThat(response.getPaidPrincipalAmount()).isEqualTo(20_000L);
        assertThat(response.getPaidInterestAmount()).isEqualTo(1_000L);
        verifyNoInteractions(transferService, walletMapper);
        verify(mapper, never()).selectLoanRepaymentForChildForUpdate(any(), any());
    }

    @Test
    @DisplayName("조기상환 예상 조회는 지갑과 계약 상태를 변경하지 않는다")
    void quoteDoesNotMutateState() {
        LoanRepaymentVO loan = activeLoan(100_000L, 1_000L);
        when(mapper.selectLoanRepaymentForRead(2L, 7L)).thenReturn(loan);

        LoanEarlyRepaymentResponseDTO response = service.quote(CHILD, 7L, 21_000L);

        assertThat(response.getCurrentOutstandingPrincipal()).isEqualTo(100_000L);
        assertThat(response.getCurrentOverdueInterest()).isEqualTo(1_000L);
        assertThat(response.getPaidInterestAmount()).isEqualTo(1_000L);
        assertThat(response.getPaidPrincipalAmount()).isEqualTo(20_000L);
        assertThat(response.isExecuted()).isFalse();
        verifyNoInteractions(transferService, walletMapper, scoreChangeService);
    }

    private void stubWallets(long childBalance) {
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(10L, childBalance));
        when(walletMapper.selectWalletForUpdate(10L)).thenReturn(wallet(10L, childBalance));
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(11L, 0L));
    }

    private void stubTransfer() {
        TransferVO transfer = new TransferVO();
        transfer.setId(30L);
        when(transferService.transferInExistingTransaction(
                anyLong(), anyLong(), anyLong(), eq(TransferType.LOAN), anyString()))
                .thenReturn(transfer);
    }

    private LoanRepaymentVO activeLoan(long outstandingPrincipal, long overdueInterest) {
        LoanRepaymentVO loan = new LoanRepaymentVO();
        loan.setEnrollmentId(7L);
        loan.setParentId(1L);
        loan.setChildId(2L);
        loan.setPrincipalAmount(100_000L);
        loan.setOutstandingPrincipal(outstandingPrincipal);
        loan.setOverdueInterest(overdueInterest);
        loan.setAppliedRate(new BigDecimal("7.00"));
        loan.setAppliedLateFeeRate(new BigDecimal("8.00"));
        loan.setPaymentDay(15);
        loan.setPaidCount(0);
        loan.setTermMonths(12);
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
