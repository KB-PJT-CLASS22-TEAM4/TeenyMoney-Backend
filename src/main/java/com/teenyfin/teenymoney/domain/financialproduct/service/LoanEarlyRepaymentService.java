package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.request.LoanEarlyRepaymentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.LoanEarlyRepaymentResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자녀가 언제든 원하는 금액으로 대출 원금을 미리 갚는 조기상환 유스케이스다.
 * 매달 자동상환(LoanRepaymentProcessor)과 같은 계약 락을 공유해 동시 정산을 막는다.
 * 조기상환 패널티는 없고, 요청 금액은 연체이자부터 충당한 뒤 남는 만큼 원금을 줄인다.
 */
@Service
public class LoanEarlyRepaymentService {
    private static final int NO_SCORE_CHANGE = 0;
    private static final int SCHEDULED_INSTALLMENT_NO = 0;

    private final FinancialProductMapper mapper;
    private final WalletMapper walletMapper;
    private final TransferService transferService;
    private final TeenyScorePolicyService scorePolicyService;
    private final TeenyScoreChangeService scoreChangeService;

    public LoanEarlyRepaymentService(
            FinancialProductMapper mapper,
            WalletMapper walletMapper,
            TransferService transferService,
            TeenyScorePolicyService scorePolicyService,
            TeenyScoreChangeService scoreChangeService) {
        this.mapper = mapper;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
        this.scorePolicyService = scorePolicyService;
        this.scoreChangeService = scoreChangeService;
    }

    /** 지갑과 계약 상태는 바꾸지 않고 요청 금액이 이자·원금에 어떻게 나뉠지만 계산한다. */
    @Transactional(readOnly = true)
    public LoanEarlyRepaymentResponseDTO quote(
            MemberPrincipal principal, Long enrollmentId, long amount) {
        Long childId = requireChild(principal);
        LoanRepaymentVO loan = requireRepayableLoan(
                mapper.selectLoanRepaymentForRead(childId, enrollmentId));
        Calculation calculation = calculate(loan, amount);
        return response(loan, calculation, amount, false);
    }

    /** 계약 잠금부터 송금, 상태 변경, 점수까지 한 트랜잭션으로 처리한다. */
    @Transactional
    public LoanEarlyRepaymentResponseDTO repay(
            MemberPrincipal principal, Long enrollmentId,
            LoanEarlyRepaymentRequestDTO request) {
        Long childId = requireChild(principal);

        // 같은 idempotencyKey로 재요청이 왔으면(네트워크 재시도 등) 지갑을 다시 건드리지 않고
        // 최초 처리 결과를 그대로 돌려준다. 이 체크가 락보다 먼저라 재요청은 계약을 잠그지도 않는다.
        LoanEarlyRepaymentHistoryVO existing = mapper.selectLoanEarlyRepaymentByIdempotencyKey(
                enrollmentId, request.getIdempotencyKey());
        if (existing != null) {
            return retriedResponse(childId, enrollmentId, request.getAmount(), existing);
        }

        // 매달 자동상환 스케줄러(LoanRepaymentProcessor)와 같은 FOR UPDATE 락을 사용해
        // 같은 계약을 동시에 정산하지 못하게 한다.
        LoanRepaymentVO loan = requireRepayableLoan(
                mapper.selectLoanRepaymentForChildForUpdate(childId, enrollmentId));
        Calculation calculation = calculate(loan, request.getAmount());

        // 부분 상환은 지원하지 않는다: 잔액이 요청 금액보다 적으면 일부만 갚지 않고 그대로 거절한다.
        // (매달 자동상환의 부분납부와 달리, 여기는 자녀가 스스로 금액을 정해서 요청하는 경로라서
        //  "낼 수 있는 만큼만"이 아니라 "요청한 금액 그대로"가 원칙이다.)
        WalletVO childWallet = requireMemberWallet(loan.getChildId());
        WalletVO lockedChildWallet = requireLockedWallet(childWallet.getId());
        if (lockedChildWallet.getBalance() < request.getAmount()) {
            throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }
        WalletVO parentWallet = requireMemberWallet(loan.getParentId());

        TransferVO transfer = transferService.transferInExistingTransaction(
                lockedChildWallet.getId(), parentWallet.getId(), request.getAmount(),
                TransferType.LOAN, request.getIdempotencyKey());

        // installment_no는 정규 회차(1~termMonths)와 겹치지 않게 0을 쓰고,
        // repayment_type='EARLY'로 자동상환 이력과 구분한다.
        mapper.insertLoanRepaymentHistory(
                enrollmentId, transfer.getId(), SCHEDULED_INSTALLMENT_NO, "EARLY",
                calculation.paidPrincipal, calculation.paidPrincipal,
                calculation.paidInterest, calculation.paidInterest,
                "PAID", null);

        long newOutstanding = loan.getOutstandingPrincipal() - calculation.paidPrincipal;
        long newOverdueInterest = loan.getOverdueInterest() - calculation.paidInterest;
        boolean repaid = newOutstanding == 0 && newOverdueInterest == 0;
        // 아직 다 안 갚았으면 ACTIVE/OVERDUE/DEFAULTED였던 기존 상태를 그대로 유지한다.
        // (조기상환만으로는 연체 여부를 재판정하지 않는다 — 그건 다음 정규 상환에서 스케줄러가 담당)
        String newStatus = repaid ? "REPAID" : loan.getStatus();
        int updated = mapper.updateLoanAfterRepayment(
                enrollmentId, newOutstanding, newOverdueInterest,
                loan.getPaidCount(), newStatus);
        if (updated != 1) {
            throw new IllegalStateException("대출 조기상환 상태 변경에 실패했습니다.");
        }

        // 완제된 경우에만 점수를 반영한다. 만기 정상완납과 같은 정책(loanMaturity)을 재사용하는데,
        // 이벤트 키가 "LOAN_REPAID:{enrollmentId}"로 계약당 한 번뿐이라 스케줄러의 정상완납과
        // 조기상환의 완제가 같은 계약에서 동시에 중복 반영될 일은 없다.
        int scoreChange = NO_SCORE_CHANGE;
        if (repaid) {
            var scoreRequest = scorePolicyService.loanMaturity(loan.getChildId(), enrollmentId);
            scoreChangeService.change(scoreRequest);
            scoreChange = scoreRequest.getAmount();
        }

        return new LoanEarlyRepaymentResponseDTO(
                enrollmentId, request.getAmount(),
                loan.getOutstandingPrincipal(), loan.getOverdueInterest(),
                calculation.paidInterest, calculation.paidPrincipal,
                newOutstanding, newOverdueInterest,
                newStatus, scoreChange, true);
    }

    /** 네트워크 재시도라면 잔액을 다시 차감하지 않고 최초 성공 결과를 그대로 돌려준다. */
    private LoanEarlyRepaymentResponseDTO retriedResponse(
            Long childId, Long enrollmentId, long requestedAmount,
            LoanEarlyRepaymentHistoryVO existing) {
        long paidTotal = Math.addExact(
                existing.getPaidPrincipalAmount(), existing.getPaidInterestAmount());
        if (requestedAmount != paidTotal) {
            throw new BusinessException(WalletErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        LoanRepaymentVO loan = mapper.selectLoanRepaymentForRead(childId, enrollmentId);
        if (loan == null) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_ENROLLMENT_NOT_FOUND);
        }
        // 이미 반영된 이력이라 지금 남은 값에 그때 갚은 만큼을 더해서 "그 시점의 상환 전" 값을 복원한다.
        long currentOutstandingPrincipal = Math.addExact(
                loan.getOutstandingPrincipal(), existing.getPaidPrincipalAmount());
        long currentOverdueInterest = Math.addExact(
                loan.getOverdueInterest(), existing.getPaidInterestAmount());
        return new LoanEarlyRepaymentResponseDTO(
                enrollmentId, requestedAmount,
                currentOutstandingPrincipal, currentOverdueInterest,
                existing.getPaidInterestAmount(), existing.getPaidPrincipalAmount(),
                loan.getOutstandingPrincipal(), loan.getOverdueInterest(),
                loan.getStatus(), NO_SCORE_CHANGE, true);
    }

    /**
     * quote()와 repay()가 완전히 같은 계산을 쓰도록 공유한다 — 화면에서 미리 본 금액과
     * 실제 처리 금액이 어긋나지 않게 하기 위해서다(예·적금 중도해지와 동일한 이유).
     * 이자 신규 발생분(일할계산)은 계산하지 않는다: 연체이자(overdue_interest)는 스케줄러가
     * 이미 쌓아둔 값만 정산 대상이고, 아직 밀리지 않은 정상 이자는 다음 정규 상환에서 처리한다.
     */
    private Calculation calculate(LoanRepaymentVO loan, long amount) {
        long totalDue = Math.addExact(
                loan.getOutstandingPrincipal(), loan.getOverdueInterest());
        if (amount > totalDue) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_LOAN_EARLY_REPAYMENT_AMOUNT_EXCEEDED);
        }
        // 연체이자부터 충당하고 남는 만큼만 원금을 줄인다. 수수료·페널티는 없다.
        long paidInterest = Math.min(amount, loan.getOverdueInterest());
        long paidPrincipal = Math.min(loan.getOutstandingPrincipal(), amount - paidInterest);
        return new Calculation(paidInterest, paidPrincipal);
    }

    private LoanEarlyRepaymentResponseDTO response(
            LoanRepaymentVO loan, Calculation calculation, long amount, boolean executed) {
        long remainingOutstanding = loan.getOutstandingPrincipal() - calculation.paidPrincipal;
        long remainingOverdueInterest = loan.getOverdueInterest() - calculation.paidInterest;
        boolean wouldRepay = remainingOutstanding == 0 && remainingOverdueInterest == 0;
        int scoreChange = wouldRepay
                ? scorePolicyService.loanMaturity(
                        loan.getChildId(), loan.getEnrollmentId()).getAmount()
                : NO_SCORE_CHANGE;
        String status = wouldRepay ? "REPAID" : loan.getStatus();
        return new LoanEarlyRepaymentResponseDTO(
                loan.getEnrollmentId(), amount,
                loan.getOutstandingPrincipal(), loan.getOverdueInterest(),
                calculation.paidInterest, calculation.paidPrincipal,
                remainingOutstanding, remainingOverdueInterest,
                status, scoreChange, executed);
    }

    private LoanRepaymentVO requireRepayableLoan(LoanRepaymentVO loan) {
        if (loan == null) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_ENROLLMENT_NOT_FOUND);
        }
        // DEFAULTED는 만기 후 미상환이 이어지며 매달 감점되는 상태라 조기상환으로 벗어날 수 있어야 한다.
        // PENDING/REJECTED는 원금이 실행되지 않은 상태, REPAID는 이미 완제된 상태라 대상에서 제외한다.
        if (!("ACTIVE".equals(loan.getStatus()) || "OVERDUE".equals(loan.getStatus())
                || "DEFAULTED".equals(loan.getStatus()))) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_LOAN_EARLY_REPAYMENT_NOT_AVAILABLE);
        }
        return loan;
    }

    private Long requireChild(MemberPrincipal principal) {
        if (principal == null || !"CHILD".equals(principal.role())) {
            throw new BusinessException(FinancialProductErrorCode.FINANCIAL_PRODUCT_CHILD_ONLY);
        }
        return principal.memberId();
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

    private record Calculation(long paidInterest, long paidPrincipal) {
    }
}
