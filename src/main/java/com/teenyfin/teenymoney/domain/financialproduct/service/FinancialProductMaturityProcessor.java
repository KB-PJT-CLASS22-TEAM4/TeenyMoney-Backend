package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductMaturityVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingContributionVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.teenyscore.dto.request.TeenyScoreChangeRequestDTO;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreEventRecordVO;
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
import java.util.Set;

/**
 * 한 가입 건의 원금·이자 송금, 점수, 상태 변경을 하나의 신규 트랜잭션에서 처리한다.
 * 어느 한 단계라도 실패하면 해당 가입 건 전체가 롤백되어 부분 정산이 남지 않는다.
 */
@Service
public class FinancialProductMaturityProcessor {
    // 만기 6개월 미만 상품은 연속만기 스트릭 판정 대상에서 제외한다.
    private static final int CONSECUTIVE_MATURITY_MIN_TERM_MONTHS = 6;
    private static final Set<String> MATURED_EVENT_CODES = Set.of(
            "DEPOSIT_MATURED", "SAVING_FIXED_MATURED", "SAVING_FREE_MATURED");

    private final FinancialProductMapper financialProductMapper;
    private final WalletMapper walletMapper;
    private final TransferService transferService;
    private final TeenyScorePolicyService scorePolicyService;
    private final TeenyScoreChangeService scoreChangeService;
    private final FinancialProductInterestCalculator interestCalculator;
    private final NotificationService notificationService;
    private final MemberMapper memberMapper;
    private final TeenyScoreMapper teenyScoreMapper;

    public FinancialProductMaturityProcessor(
            FinancialProductMapper financialProductMapper,
            WalletMapper walletMapper,
            TransferService transferService,
            TeenyScorePolicyService scorePolicyService,
            TeenyScoreChangeService scoreChangeService,
            FinancialProductInterestCalculator interestCalculator,
            NotificationService notificationService,
            MemberMapper memberMapper,
            TeenyScoreMapper teenyScoreMapper) {
        this.financialProductMapper = financialProductMapper;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
        this.scorePolicyService = scorePolicyService;
        this.scoreChangeService = scoreChangeService;
        this.interestCalculator = interestCalculator;
        this.notificationService = notificationService;
        this.memberMapper = memberMapper;
        this.teenyScoreMapper = teenyScoreMapper;
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
        transferInterestOrFail(parentWallet.getId(), childWallet.getId(), interest,
                TransferType.DEPOSIT, "DPT_MAT:" + enrollmentId + ":I",
                FinancialProductType.DEPOSIT, maturity);
        // 이번 만기가 이력에 쌓이기 전에 먼저 "직전" 이벤트를 확인해야 자기 자신과 비교하지 않는다.
        applyConsecutiveMaturityBonusIfEligible(
                maturity.getChildId(), enrollmentId, maturity.getTermMonths());
        scoreChangeService.change(scorePolicyService.depositMaturity(
                maturity.getChildId(), enrollmentId, maturity.getTermMonths()));
        requireUpdated(financialProductMapper.markDepositMatured(enrollmentId));
        notifyMaturity(FinancialProductType.DEPOSIT, maturity, principal, interest);
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
        transferInterestOrFail(parentWallet.getId(), childWallet.getId(), interest,
                TransferType.SAVING, "SVG_MAT:" + enrollmentId + ":I",
                FinancialProductType.SAVING, maturity);
        applySavingMaturityScore(maturity, contributions);
        requireUpdated(financialProductMapper.markSavingMatured(enrollmentId));
        notifyMaturity(FinancialProductType.SAVING, maturity, recordedPrincipal, interest);
    }

    /**
     * 자녀에게는 받은 금액을, 부모에게는 지갑에서 빠져나간 이자를 알린다.
     * 정산과 같은 트랜잭션에 합류하므로 만기 처리가 롤백되면 알림 이력도 함께 사라진다.
     */
    private void notifyMaturity(
            FinancialProductType type, FinancialProductMaturityVO maturity,
            long principal, long interest) {
        Long enrollmentId = maturity.getEnrollmentId();
        notificationService.createNotification(
                maturity.getChildId(),
                FinancialProductNotificationMessages.maturityChildTitle(type),
                FinancialProductNotificationMessages.maturityChildContent(
                        maturity.getProductName(), principal, interest),
                FinancialProductNotificationMessages.maturityReferenceType(type),
                enrollmentId, true);

        // 이자가 0원이면 부모 지갑에서 나간 돈이 없으므로 부모에게는 알리지 않는다.
        if (interest <= 0) {
            return;
        }
        String childName = memberMapper.selectById(maturity.getChildId()).getName();
        notificationService.createNotification(
                maturity.getParentId(),
                FinancialProductNotificationMessages.maturityParentTitle(type, childName),
                FinancialProductNotificationMessages.maturityParentContent(
                        maturity.getProductName(), interest),
                FinancialProductNotificationMessages.maturityReferenceType(type),
                enrollmentId, true);
    }

    private void applySavingMaturityScore(
            FinancialProductMaturityVO maturity,
            List<SavingContributionVO> contributions) {
        int termMonths = maturity.getTermMonths();
        int paymentRate = percentage(
                contributions.stream().mapToLong(SavingContributionVO::getPaidAmount).sum(),
                Math.multiplyExact(maturity.getMonthlyAmount(), (long) termMonths));
        TeenyScoreChangeRequestDTO scoreRequest;
        if ("FIXED".equals(maturity.getSavingsType())) {
            Integer firstMissed = financialProductMapper
                    .selectFirstMissedSavingInstallment(maturity.getEnrollmentId());
            int progress = firstMissed == null ? 0
                    : Math.min(99, (firstMissed - 1) * 100 / termMonths);
            scoreRequest = scorePolicyService.fixedSavingMaturity(
                    maturity.getChildId(), maturity.getEnrollmentId(), termMonths,
                    paymentRate, progress);
        } else {
            int firstShortfallProgress = firstFreeShortfallProgress(maturity, contributions);
            scoreRequest = scorePolicyService.freeSavingMaturity(
                    maturity.getChildId(), maturity.getEnrollmentId(), termMonths,
                    paymentRate, firstShortfallProgress);
        }
        // 납입률 미달로 실제로는 중도해지 취급된 경우는 연속만기 스트릭 대상이 아니다.
        // 이 이벤트가 이력에 쌓이기 전에 먼저 "직전" 이벤트를 확인해야 자기 자신과 비교하지 않는다.
        if (MATURED_EVENT_CODES.contains(scoreRequest.getEventCode().name())) {
            applyConsecutiveMaturityBonusIfEligible(
                    maturity.getChildId(), maturity.getEnrollmentId(), termMonths);
        }
        scoreChangeService.change(scoreRequest);
    }

    /**
     * 이번 만기가 6개월 이상이고, 직전 만기/해지 이벤트도 6개월 이상 상품의 정상 만기였으면
     * 연속만기 보너스를 반영한다. 직전 이벤트의 가입 기간은 이력에 저장돼 있지 않으므로
     * reference로 원 가입 건을 다시 조회해서 확인한다.
     */
    private void applyConsecutiveMaturityBonusIfEligible(
            Long childId, Long enrollmentId, int termMonths) {
        if (termMonths < CONSECUTIVE_MATURITY_MIN_TERM_MONTHS) return;
        // 서로 다른 계약이 동시에 만기돼도 같은 자녀의 직전 이력을 한 요청씩 판정한다.
        scoreChangeService.lockChildScore(childId);
        List<TeenyScoreEventRecordVO> recent =
                teenyScoreMapper.selectRecentFinalSavingEvents(childId, 1);
        if (recent.isEmpty()) return;
        TeenyScoreEventRecordVO last = recent.get(0);
        if (!MATURED_EVENT_CODES.contains(last.getEventCode())) return;
        if (!isLongTermEnrollment(childId, last)) return;
        scoreChangeService.change(
                scorePolicyService.consecutiveMaturityBonus(childId, enrollmentId));
    }

    private boolean isLongTermEnrollment(Long childId, TeenyScoreEventRecordVO event) {
        Integer termMonths = switch (event.getReferenceType()) {
            case "DEPOSIT_ENROLLMENT" -> {
                var enrollment = financialProductMapper
                        .selectDepositEnrollmentByChildIdAndId(childId, event.getReferenceId());
                yield enrollment == null ? null : enrollment.getTermMonths();
            }
            case "SAVING_ENROLLMENT" -> {
                var enrollment = financialProductMapper
                        .selectSavingEnrollmentByChildIdAndId(childId, event.getReferenceId());
                yield enrollment == null ? null : enrollment.getTermMonths();
            }
            default -> null;
        };
        return termMonths != null && termMonths >= CONSECUTIVE_MATURITY_MIN_TERM_MONTHS;
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

    /**
     * 부모 지갑 잔액이 부족해 이자를 못 보내면, 원인을 담은 전용 예외로 바꿔 던진다.
     * 이 메서드가 속한 트랜잭션은 그대로 롤백되고, 알림은 호출부가 롤백 밖에서 별도로 보낸다.
     */
    private void transferInterestOrFail(
            Long from, Long to, long interest, TransferType transferType, String key,
            FinancialProductType productType, FinancialProductMaturityVO maturity) {
        if (interest <= 0) return;
        try {
            transferService.transferInExistingTransaction(from, to, interest, transferType, key);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != WalletErrorCode.INSUFFICIENT_BALANCE) {
                throw exception;
            }
            throw new FinancialProductInterestPaymentFailedException(
                    maturity.getEnrollmentId(), maturity.getChildId(), maturity.getParentId(),
                    maturity.getProductName(), productType, interest, exception);
        }
    }

    /**
     * 실패한 정산 트랜잭션과 별개로 새 트랜잭션에서 부모에게만 알린다.
     * 자녀는 원금·이자 둘 다 못 받은 상태라 "지급됐다"는 알림을 보낼 수 없으므로 보내지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyInterestPaymentFailed(FinancialProductInterestPaymentFailedException failure) {
        notificationService.createNotification(
                failure.getParentId(),
                FinancialProductNotificationMessages.maturityInterestFailedTitle(failure.getType()),
                FinancialProductNotificationMessages.maturityInterestFailedContent(
                        failure.getProductName(), failure.getInterest()),
                FinancialProductNotificationMessages.maturityReferenceType(failure.getType()),
                failure.getEnrollmentId(), true);
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
