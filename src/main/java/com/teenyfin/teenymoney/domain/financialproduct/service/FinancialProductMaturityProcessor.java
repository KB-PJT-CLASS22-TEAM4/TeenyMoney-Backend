package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductMaturityVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingContributionVO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 한 가입 건의 원금·이자 송금, 점수, 상태 변경을 하나의 신규 트랜잭션에서 처리한다.
 * 어느 한 단계라도 실패하면 해당 가입 건 전체가 롤백되어 부분 정산이 남지 않는다.
 */
@Service
public class FinancialProductMaturityProcessor {
    private final FinancialProductMapper financialProductMapper;
    private final WalletMapper walletMapper;
    private final TransferService transferService;
    private final TeenyScorePolicyService scorePolicyService;
    private final TeenyScoreChangeService scoreChangeService;
    private final FinancialProductInterestCalculator interestCalculator;

    public FinancialProductMaturityProcessor(
            FinancialProductMapper financialProductMapper,
            WalletMapper walletMapper,
            TransferService transferService,
            TeenyScorePolicyService scorePolicyService,
            TeenyScoreChangeService scoreChangeService,
            FinancialProductInterestCalculator interestCalculator) {
        this.financialProductMapper = financialProductMapper;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
        this.scorePolicyService = scorePolicyService;
        this.scoreChangeService = scoreChangeService;
        this.interestCalculator = interestCalculator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processDeposit(Long enrollmentId, LocalDate processingDate) {
        // ACTIVE 조건과 FOR UPDATE를 함께 사용해 동시 배치가 같은 계약을 두 번 정산하지 못하게 한다.
        FinancialProductMaturityVO maturity = financialProductMapper
                .selectDepositMaturityForUpdate(enrollmentId, processingDate);
        if (maturity == null) return;

        WalletVO productWallet = requireLockedWallet(maturity.getProductWalletId());
        WalletVO childWallet = requireMemberWallet(maturity.getChildId());
        WalletVO parentWallet = requireMemberWallet(maturity.getParentId());
        long principal = productWallet.getBalance();
        long interest = interestCalculator.calculate(
                principal, maturity.getAppliedRate(),
                maturity.getInterestCalculationType(), maturity.getStartDate(),
                maturity.getMaturityDate());

        // 원금은 상품 지갑, 이자는 요구사항에 따라 부모 회원 지갑에서 지급한다.
        transferIfPositive(productWallet.getId(), childWallet.getId(), principal,
                TransferType.DEPOSIT,
                "DPT_MAT:" + enrollmentId + ":P");
        transferIfPositive(parentWallet.getId(), childWallet.getId(), interest,
                TransferType.DEPOSIT,
                "DPT_MAT:" + enrollmentId + ":I");
        scoreChangeService.change(scorePolicyService.depositMaturity(
                maturity.getChildId(), enrollmentId, maturity.getTermMonths()));
        requireUpdated(financialProductMapper.markDepositMatured(enrollmentId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSaving(Long enrollmentId, LocalDate processingDate) {
        FinancialProductMaturityVO maturity = financialProductMapper
                .selectSavingMaturityForUpdate(enrollmentId, processingDate);
        if (maturity == null) return;

        WalletVO productWallet = requireLockedWallet(maturity.getProductWalletId());
        WalletVO childWallet = requireMemberWallet(maturity.getChildId());
        WalletVO parentWallet = requireMemberWallet(maturity.getParentId());
        List<SavingContributionVO> contributions = financialProductMapper
                .selectSavingContributions(enrollmentId);
        long recordedPrincipal = contributions.stream()
                .mapToLong(SavingContributionVO::getPaidAmount).sum();
        // 이력과 실제 잔액이 다르면 임의 보정하지 않는다. 원인을 확인할 수 있도록 해당 건만 롤백한다.
        if (recordedPrincipal != productWallet.getBalance()) {
            throw new IllegalStateException("적금 납입 이력과 상품 지갑 잔액이 일치하지 않습니다.");
        }
        long interest = contributions.stream().mapToLong(contribution ->
                interestCalculator.calculate(
                        contribution.getPaidAmount(), maturity.getAppliedRate(),
                        maturity.getInterestCalculationType(),
                        contribution.getPaidAt().toLocalDate(), maturity.getMaturityDate()))
                .sum();

        transferIfPositive(productWallet.getId(), childWallet.getId(), recordedPrincipal,
                TransferType.SAVING,
                "SVG_MAT:" + enrollmentId + ":P");
        transferIfPositive(parentWallet.getId(), childWallet.getId(), interest,
                TransferType.SAVING,
                "SVG_MAT:" + enrollmentId + ":I");
        applySavingMaturityScore(maturity, contributions);
        requireUpdated(financialProductMapper.markSavingMatured(enrollmentId));
    }

    private void applySavingMaturityScore(
            FinancialProductMaturityVO maturity,
            List<SavingContributionVO> contributions) {
        int termMonths = maturity.getTermMonths();
        int paymentRate = percentage(
                contributions.stream().mapToLong(SavingContributionVO::getPaidAmount).sum(),
                Math.multiplyExact(maturity.getMonthlyAmount(), (long) termMonths));
        if ("FIXED".equals(maturity.getSavingsType())) {
            Integer firstMissed = financialProductMapper
                    .selectFirstMissedSavingInstallment(maturity.getEnrollmentId());
            int progress = firstMissed == null ? 0
                    : Math.min(99, (firstMissed - 1) * 100 / termMonths);
            scoreChangeService.change(scorePolicyService.fixedSavingMaturity(
                    maturity.getChildId(), maturity.getEnrollmentId(), termMonths,
                    paymentRate, progress));
            return;
        }
        int firstShortfallProgress = firstFreeShortfallProgress(maturity, contributions);
        scoreChangeService.change(scorePolicyService.freeSavingMaturity(
                maturity.getChildId(), maturity.getEnrollmentId(), termMonths,
                paymentRate, firstShortfallProgress));
    }

    private int firstFreeShortfallProgress(
            FinancialProductMaturityVO maturity,
            List<SavingContributionVO> contributions) {
        YearMonth firstMonth = YearMonth.from(maturity.getStartDate());
        for (int index = 0; index < maturity.getTermMonths(); index++) {
            YearMonth month = firstMonth.plusMonths(index);
            long paid = contributions.stream()
                    .filter(c -> YearMonth.from(c.getPaidAt()).equals(month))
                    .mapToLong(SavingContributionVO::getPaidAmount).sum();
            if (paid < maturity.getMonthlyAmount()) {
                return Math.min(99, index * 100 / maturity.getTermMonths());
            }
        }
        return 0;
    }

    private int percentage(long paid, long target) {
        if (target <= 0) return 0;
        return (int) Math.min(100, paid * 100 / target);
    }

    private void transferIfPositive(Long from, Long to, long amount,
                                    TransferType type, String key) {
        if (amount <= 0) return;
        // 가입 ID 기반 키를 재사용하므로 스케줄러 재실행에도 같은 송금은 한 번만 완료된다.
        transferService.transferInExistingTransaction(from, to, amount, type, key);
    }

    private WalletVO requireLockedWallet(Long walletId) {
        if (walletId == null) throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        WalletVO wallet = walletMapper.selectWalletForUpdate(walletId);
        if (wallet == null) throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        return wallet;
    }

    private WalletVO requireMemberWallet(Long memberId) {
        WalletVO wallet = walletMapper.selectMemberWalletByMemberId(memberId);
        if (wallet == null) throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        return wallet;
    }

    private void requireUpdated(int updated) {
        if (updated != 1) throw new IllegalStateException("만기 상태 변경에 실패했습니다.");
    }
}
