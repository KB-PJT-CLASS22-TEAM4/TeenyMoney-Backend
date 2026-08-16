package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** 한 대출의 도래한 미처리 회차를 오래된 순서대로 자동상환한다. */
@Service
public class LoanRepaymentProcessor {
    private final FinancialProductMapper mapper;
    private final WalletMapper walletMapper;
    private final TransferService transferService;
    private final TeenyScorePolicyService scorePolicyService;
    private final TeenyScoreChangeService scoreChangeService;
    private final LoanRepaymentCalculator calculator;

    public LoanRepaymentProcessor(
            FinancialProductMapper mapper, WalletMapper walletMapper,
            TransferService transferService,
            TeenyScorePolicyService scorePolicyService,
            TeenyScoreChangeService scoreChangeService,
            LoanRepaymentCalculator calculator) {
        this.mapper = mapper;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
        this.scorePolicyService = scorePolicyService;
        this.scoreChangeService = scoreChangeService;
        this.calculator = calculator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long enrollmentId, LocalDate processingDate) {
        // 계약 잠금으로 중복 스케줄러와 향후 직접상환 API가 같은 잔액을 동시에 정산하지 못하게 한다.
        LoanRepaymentVO loan = mapper.selectLoanRepaymentForUpdate(enrollmentId);
        if (loan == null || !("ACTIVE".equals(loan.getStatus())
                || "OVERDUE".equals(loan.getStatus())
                || "DEFAULTED".equals(loan.getStatus()))) return;

        LocalDate firstDueDate = firstDueDate(loan);
        // 배치가 며칠 중단되었더라도 과거 미처리 회차부터 순서대로 따라잡아 처리한다.
        for (int installmentNo = 1; installmentNo <= loan.getTermMonths(); installmentNo++) {
            LocalDate dueDate = firstDueDate.plusMonths(installmentNo - 1L);
            if (dueDate.isAfter(processingDate)) break;
            // 회차 이력이 멱등성 기준이다. 같은 날짜의 배치를 재실행해도 송금과 점수를 반복하지 않는다.
            if (mapper.countLoanRepaymentHistory(enrollmentId, installmentNo) > 0) continue;
            processInstallment(loan, installmentNo, dueDate);
            if ("REPAID".equals(loan.getStatus())) break;
        }

        if (!"REPAID".equals(loan.getStatus())
                && !processingDate.isBefore(loan.getMaturityDate())) {
            processPostMaturity(loan, processingDate);
        }
    }

    /**
     * 만기일까지 남은 원금이나 이자가 있으면 DEFAULTED로 확정한 뒤에도
     * 하루 한 번 자녀 지갑의 가용 잔액만큼 자동상환을 계속 시도한다.
     */
    private void processPostMaturity(LoanRepaymentVO loan, LocalDate processingDate) {
        if (!"DEFAULTED".equals(loan.getStatus())) {
            requireLoanUpdated(mapper.updateLoanAfterRepayment(
                    loan.getEnrollmentId(), loan.getOutstandingPrincipal(),
                    loan.getOverdueInterest(), loan.getPaidCount(), "DEFAULTED"));
            scoreChangeService.change(scorePolicyService.loanDefault(
                    loan.getChildId(), loan.getEnrollmentId()));
            loan.setStatus("DEFAULTED");
        }

        // 만기 다음 날부터 미상환 상태가 이어지는 달마다 고정 감점을 한 번만 적용한다.
        if (processingDate.isAfter(loan.getMaturityDate())) {
            scoreChangeService.change(scorePolicyService.loanPostMaturityOverdue(
                    loan.getChildId(), loan.getEnrollmentId(), YearMonth.from(processingDate)));
        }

        int finalInstallment = loan.getTermMonths();
        if (mapper.countLoanRepaymentHistoryOnDate(
                loan.getEnrollmentId(), finalInstallment, processingDate) > 0) {
            return;
        }

        LocalDate lastAttemptDate = mapper.selectLastLoanRepaymentAttemptDate(
                loan.getEnrollmentId(), finalInstallment);
        LocalDate interestFrom = lastAttemptDate == null
                ? loan.getMaturityDate() : lastAttemptDate;
        long elapsedDays = Math.max(0L, ChronoUnit.DAYS.between(
                interestFrom, processingDate));
        long additionalLateInterest = lateInterest(
                Math.addExact(loan.getOutstandingPrincipal(), loan.getOverdueInterest()),
                loan.getAppliedLateFeeRate(), elapsedDays);
        long interestDue = Math.addExact(
                loan.getOverdueInterest(), additionalLateInterest);
        long principalDue = loan.getOutstandingPrincipal();
        long totalDue = Math.addExact(principalDue, interestDue);

        WalletVO childWallet = requireMemberWallet(loan.getChildId());
        WalletVO lockedChildWallet = requireLockedWallet(childWallet.getId());
        WalletVO parentWallet = requireMemberWallet(loan.getParentId());
        long paidAmount = Math.min(lockedChildWallet.getBalance(), totalDue);
        long paidInterest = Math.min(paidAmount, interestDue);
        long paidPrincipal = Math.min(principalDue, paidAmount - paidInterest);

        Long transferId = null;
        if (paidAmount > 0) {
            TransferVO transfer = transferService.transferInExistingTransaction(
                    lockedChildWallet.getId(), parentWallet.getId(), paidAmount,
                    TransferType.LOAN, "LOAN:OVERDUE:" + loan.getEnrollmentId()
                            + ":" + processingDate);
            transferId = transfer.getId();
        }

        long newOutstanding = principalDue - paidPrincipal;
        long newOverdueInterest = interestDue - paidInterest;
        boolean repaid = newOutstanding == 0 && newOverdueInterest == 0;
        String historyStatus = paidAmount == totalDue
                ? "PAID" : paidAmount == 0 ? "OVERDUE" : "PARTIAL";
        String enrollmentStatus = repaid ? "REPAID" : "DEFAULTED";

        mapper.insertLoanRepaymentHistory(
                loan.getEnrollmentId(), transferId, finalInstallment,
                principalDue, paidPrincipal, interestDue, paidInterest,
                historyStatus, processingDate);
        requireLoanUpdated(mapper.updateLoanAfterRepayment(
                loan.getEnrollmentId(), newOutstanding, newOverdueInterest,
                loan.getPaidCount(), enrollmentStatus));

        loan.setOutstandingPrincipal(newOutstanding);
        loan.setOverdueInterest(newOverdueInterest);
        loan.setStatus(enrollmentStatus);
    }

    private void processInstallment(
            LoanRepaymentVO loan, int installmentNo, LocalDate dueDate) {
        LoanRepaymentCalculator.Installment scheduled =
                calculator.calculate(loan, installmentNo);
        long expectedBefore = installmentNo == 1
                ? loan.getPrincipalAmount()
                : calculator.expectedOutstandingAfter(loan, installmentNo - 1);
        // 실제 잔액이 직전 회차의 정상 잔액보다 큰 부분을 이전 회차에서 미납된 원금으로 본다.
        long previousUnpaidPrincipal = Math.max(
                0L, loan.getOutstandingPrincipal() - expectedBefore);
        long lateInterest = lateInterest(
                previousUnpaidPrincipal + loan.getOverdueInterest(),
                loan.getAppliedLateFeeRate(), installmentNo == 1
                        ? 0L : ChronoUnit.DAYS.between(
                                dueDate.minusMonths(1), dueDate));
        long interestDue = Math.addExact(
                Math.addExact(loan.getOverdueInterest(), scheduled.interest()), lateInterest);
        // 이전 회차 미납 원금도 이번 회차에 함께 청구하여 연체 원금이 만기까지 방치되지 않게 한다.
        long principalDue = installmentNo == loan.getTermMonths()
                // 마지막 회차의 scheduled.principal에는 이미 실제 미상환 원금 전액이 들어 있다.
                ? scheduled.principal()
                : Math.addExact(previousUnpaidPrincipal, scheduled.principal());
        long totalDue = Math.addExact(principalDue, interestDue);

        // 출금 지갑을 잠근 뒤 잔액을 읽어 다른 송금과의 동시 출금으로 음수 잔액이 되지 않게 한다.
        WalletVO childWallet = requireMemberWallet(loan.getChildId());
        WalletVO lockedChildWallet = requireLockedWallet(childWallet.getId());
        WalletVO parentWallet = requireMemberWallet(loan.getParentId());
        long paidAmount = Math.min(lockedChildWallet.getBalance(), totalDue);
        // 부분상환은 이자를 먼저 갚고 남은 금액만 원금에 충당한다.
        long paidInterest = Math.min(paidAmount, interestDue);
        long paidPrincipal = Math.min(
                principalDue, paidAmount - paidInterest);

        Long transferId = null;
        if (paidAmount > 0) {
            TransferVO transfer = transferService.transferInExistingTransaction(
                    lockedChildWallet.getId(), parentWallet.getId(), paidAmount,
                    TransferType.LOAN, "LOAN:" + loan.getEnrollmentId() + ":" + installmentNo);
            transferId = transfer.getId();
        }

        long newOutstanding = loan.getOutstandingPrincipal() - paidPrincipal;
        long newOverdueInterest = interestDue - paidInterest;
        long expectedAfter = calculator.expectedOutstandingAfter(loan, installmentNo);
        boolean repaid = newOutstanding == 0 && newOverdueInterest == 0;
        boolean overdue = !repaid && (newOverdueInterest > 0 || newOutstanding > expectedAfter);
        // 이력 상태는 이번 회차 결과이고, 가입 상태는 대출 전체의 잔액·연체 여부를 나타낸다.
        String historyStatus = paidAmount == totalDue
                ? "PAID" : paidAmount == 0 ? "OVERDUE" : "PARTIAL";
        String enrollmentStatus = repaid ? "REPAID" : overdue ? "OVERDUE" : "ACTIVE";

        mapper.insertLoanRepaymentHistory(
                loan.getEnrollmentId(), transferId, installmentNo,
                principalDue, paidPrincipal, interestDue, paidInterest,
                historyStatus, dueDate);
        int updated = mapper.updateLoanAfterRepayment(
                loan.getEnrollmentId(), newOutstanding, newOverdueInterest,
                "PAID".equals(historyStatus) ? loan.getPaidCount() + 1 : loan.getPaidCount(),
                enrollmentStatus);
        requireLoanUpdated(updated);

        // 부분납부와 미납은 회차 월 단위 키로 감점되고, 최종 완납은 계약 단위 키로 한 번만 가점된다.
        if (overdue) {
            scoreChangeService.change(scorePolicyService.loanOverdue(
                    loan.getChildId(), loan.getEnrollmentId(), YearMonth.from(dueDate),
                    previousUnpaidPrincipal + loan.getOverdueInterest() + lateInterest,
                    scheduled.total(), paidAmount));
        } else if (repaid) {
            scoreChangeService.change(scorePolicyService.loanMaturity(
                    loan.getChildId(), loan.getEnrollmentId()));
        }

        loan.setOutstandingPrincipal(newOutstanding);
        loan.setOverdueInterest(newOverdueInterest);
        loan.setPaidCount("PAID".equals(historyStatus)
                ? loan.getPaidCount() + 1 : loan.getPaidCount());
        loan.setStatus(enrollmentStatus);
    }

    private LocalDate firstDueDate(LoanRepaymentVO loan) {
        // 가입 당일이 납입일이어도 즉시 상환시키지 않고 다음 도래 납입일부터 시작한다.
        LocalDate sameMonth = loan.getStartDate().withDayOfMonth(loan.getPaymentDay());
        return sameMonth.isAfter(loan.getStartDate()) ? sameMonth : sameMonth.plusMonths(1);
    }

    private long lateInterest(long overdueAmount, BigDecimal annualRate, long days) {
        if (overdueAmount <= 0 || days <= 0) return 0L;
        return BigDecimal.valueOf(overdueAmount).multiply(annualRate)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(36_500), 0, RoundingMode.DOWN)
                .longValueExact();
    }

    private WalletVO requireMemberWallet(Long memberId) {
        WalletVO wallet = walletMapper.selectMemberWalletByMemberId(memberId);
        if (wallet == null) throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        return wallet;
    }

    private WalletVO requireLockedWallet(Long walletId) {
        WalletVO wallet = walletMapper.selectWalletForUpdate(walletId);
        if (wallet == null) throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        return wallet;
    }

    private void requireLoanUpdated(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("대출 상환 상태 변경에 실패했습니다.");
        }
    }
}
