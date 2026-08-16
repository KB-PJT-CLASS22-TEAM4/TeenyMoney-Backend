package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductTerminationResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductTerminationVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingContributionVO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.request.TeenyScoreChangeRequestDTO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 자녀가 직접 실행하는 예·적금 중도해지 유스케이스다.
 * 예상 조회와 실행이 같은 계산 메서드를 사용하므로 화면에서 본 금액과 실제 정산 기준이 어긋나지 않는다.
 */
@Service
public class FinancialProductTerminationService {
    private final FinancialProductMapper financialProductMapper;
    private final WalletMapper walletMapper;
    private final TransferService transferService;
    private final TeenyScorePolicyService scorePolicyService;
    private final TeenyScoreChangeService scoreChangeService;
    private final EarlyTerminationRatePolicy ratePolicy;
    private final FinancialProductInterestCalculator interestCalculator;
    private final Clock clock;

    public FinancialProductTerminationService(
            FinancialProductMapper financialProductMapper,
            WalletMapper walletMapper,
            TransferService transferService,
            TeenyScorePolicyService scorePolicyService,
            TeenyScoreChangeService scoreChangeService,
            EarlyTerminationRatePolicy ratePolicy,
            FinancialProductInterestCalculator interestCalculator,
            Clock clock) {
        this.financialProductMapper = financialProductMapper;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
        this.scorePolicyService = scorePolicyService;
        this.scoreChangeService = scoreChangeService;
        this.ratePolicy = ratePolicy;
        this.interestCalculator = interestCalculator;
        this.clock = clock;
    }

    /** 조회 시점의 원금·이자·점수를 계산하지만 지갑과 가입 상태는 변경하지 않는다. */
    @Transactional(readOnly = true)
    public FinancialProductTerminationResponseDTO quote(
            MemberPrincipal principal, String productType, Long enrollmentId) {
        Long childId = requireChild(principal);
        FinancialProductType type = requireTerminableType(productType);
        FinancialProductTerminationVO enrollment = select(type, childId, enrollmentId, false);
        return calculate(type, enrollment, LocalDate.now(clock), false).response;
    }

    /** 계약 잠금부터 송금, 점수, 상태 변경까지 한 트랜잭션으로 처리해 일부 정산을 남기지 않는다. */
    @Transactional
    public FinancialProductTerminationResponseDTO terminate(
            MemberPrincipal principal, String productType, Long enrollmentId) {
        Long childId = requireChild(principal);
        FinancialProductType type = requireTerminableType(productType);
        LocalDate processingDate = LocalDate.now(clock);

        // 만기 스케줄러와 중도해지가 동시에 접근해도 ACTIVE 계약을 선점한 한쪽만 처리한다.
        FinancialProductTerminationVO enrollment = select(type, childId, enrollmentId, true);
        Calculation calculation = calculate(type, enrollment, processingDate, true);
        WalletVO childWallet = requireMemberWallet(enrollment.getChildId());
        WalletVO parentWallet = requireMemberWallet(enrollment.getParentId());

        transferIfPositive(enrollment.getProductWalletId(), childWallet.getId(),
                calculation.principal, transferType(type), key(type, enrollmentId, "P"));
        transferIfPositive(parentWallet.getId(), childWallet.getId(),
                calculation.interest, transferType(type), key(type, enrollmentId, "I"));
        scoreChangeService.change(calculation.scoreRequest);

        int updated = type == FinancialProductType.DEPOSIT
                ? financialProductMapper.markDepositTerminated(enrollmentId)
                : financialProductMapper.markSavingTerminated(enrollmentId);
        if (updated != 1) {
            throw new IllegalStateException("중도해지 상태 변경에 실패했습니다.");
        }
        return calculation.terminatedResponse();
    }

    private Calculation calculate(
            FinancialProductType type, FinancialProductTerminationVO enrollment,
            LocalDate processingDate, boolean execution) {
        validateTerminable(enrollment, processingDate);
        // 예상 조회는 SQL에서 함께 조회한 잔액을 사용하고, 실제 실행만 최신 잔액을 잠금 조회한다.
        long productWalletBalance = execution
                ? requireProductWallet(enrollment.getProductWalletId()).getBalance()
                : requireQuotedProductWalletBalance(enrollment);
        int progress = progress(enrollment.getStartDate(), enrollment.getMaturityDate(), processingDate);
        BigDecimal rate = ratePolicy.calculate(
                enrollment.getAppliedEarlyTerminationRate(), progress);

        long principal;
        long interest;
        if (type == FinancialProductType.DEPOSIT) {
            principal = productWalletBalance;
            interest = interestCalculator.calculate(principal, rate,
                    enrollment.getInterestCalculationType(),
                    enrollment.getStartDate(), processingDate);
        } else {
            List<SavingContributionVO> contributions = financialProductMapper
                    .selectSavingContributions(enrollment.getEnrollmentId());
            principal = contributions.stream().mapToLong(SavingContributionVO::getPaidAmount).sum();
            // 납입 이력과 상품 지갑이 다르면 잘못된 금액을 지급하지 않고 원인을 먼저 확인한다.
            if (principal != productWalletBalance) {
                throw new IllegalStateException("적금 납입 이력과 상품 지갑 잔액이 일치하지 않습니다.");
            }
            // 적금은 각 납입일이 다르므로 전체 원금에 한 번 계산하지 않고 납입 건별 보유기간을 적용한다.
            interest = contributions.stream().mapToLong(contribution ->
                    interestCalculator.calculate(contribution.getPaidAmount(), rate,
                            enrollment.getInterestCalculationType(),
                            contribution.getPaidAt().toLocalDate(), processingDate)).sum();
        }

        // 조회에서는 변동량만 응답하고, 실행 경로에서만 change()를 호출해 실제 점수를 변경한다.
        TeenyScoreChangeRequestDTO score = type == FinancialProductType.DEPOSIT
                ? scorePolicyService.depositEarlyTermination(
                        enrollment.getChildId(), enrollment.getEnrollmentId(),
                        enrollment.getTermMonths(), progress)
                : scorePolicyService.savingEarlyTermination(
                        enrollment.getChildId(), enrollment.getEnrollmentId(),
                        "FIXED".equals(enrollment.getSavingsType()),
                        enrollment.getTermMonths(), progress);
        FinancialProductTerminationResponseDTO response = new FinancialProductTerminationResponseDTO(
                enrollment.getEnrollmentId(), type.name(), progress, rate,
                principal, interest, score.getAmount(), execution);
        return new Calculation(principal, interest, score, response);
    }

    private FinancialProductTerminationVO select(
            FinancialProductType type, Long childId, Long enrollmentId, boolean lock) {
        FinancialProductTerminationVO result;
        if (type == FinancialProductType.DEPOSIT) {
            result = lock
                    ? financialProductMapper.selectDepositTerminationForUpdate(childId, enrollmentId)
                    : financialProductMapper.selectDepositTermination(childId, enrollmentId);
        } else {
            result = lock
                    ? financialProductMapper.selectSavingTerminationForUpdate(childId, enrollmentId)
                    : financialProductMapper.selectSavingTermination(childId, enrollmentId);
        }
        if (result == null) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_ENROLLMENT_NOT_FOUND);
        }
        return result;
    }

    private void validateTerminable(
            FinancialProductTerminationVO enrollment, LocalDate processingDate) {
        if (!"ACTIVE".equals(enrollment.getStatus())
                || enrollment.getStartDate() == null
                || enrollment.getMaturityDate() == null
                || !processingDate.isBefore(enrollment.getMaturityDate())) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_TERMINATION_NOT_AVAILABLE);
        }
    }

    private int progress(LocalDate start, LocalDate maturity, LocalDate current) {
        // 정책 경계와 동일하게 일수 기준 정수 백분율을 사용하며 만기 전 값은 최대 99로 제한한다.
        long totalDays = ChronoUnit.DAYS.between(start, maturity);
        long elapsedDays = Math.max(0, ChronoUnit.DAYS.between(start, current));
        if (totalDays <= 0) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_TERMINATION_NOT_AVAILABLE);
        }
        return (int) Math.min(99, elapsedDays * 100 / totalDays);
    }

    private FinancialProductType requireTerminableType(String value) {
        FinancialProductType type = FinancialProductType.from(value);
        if (type == FinancialProductType.LOAN) {
            throw new BusinessException(FinancialProductErrorCode.FINANCIAL_PRODUCT_TYPE_INVALID);
        }
        return type;
    }

    private Long requireChild(MemberPrincipal principal) {
        if (principal == null || !"CHILD".equals(principal.role())) {
            throw new BusinessException(FinancialProductErrorCode.FINANCIAL_PRODUCT_CHILD_ONLY);
        }
        return principal.memberId();
    }

    private WalletVO requireProductWallet(Long walletId) {
        if (walletId == null) throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        // 지갑 도메인에 금융상품 전용 조회를 추가하지 않고 기존 잠금 조회를 공통 사용한다.
        WalletVO wallet = walletMapper.selectWalletForUpdate(walletId);
        if (wallet == null) throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        return wallet;
    }

    private long requireQuotedProductWalletBalance(
            FinancialProductTerminationVO enrollment) {
        if (enrollment.getProductWalletBalance() == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }
        return enrollment.getProductWalletBalance();
    }

    private WalletVO requireMemberWallet(Long memberId) {
        WalletVO wallet = walletMapper.selectMemberWalletByMemberId(memberId);
        if (wallet == null) throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        return wallet;
    }

    private void transferIfPositive(
            Long from, Long to, long amount, TransferType type, String idempotencyKey) {
        // 0원 송금은 송금 도메인에서 거절되므로 이자가 0원이면 원금 정산만 수행한다.
        if (amount > 0) {
            transferService.transferInExistingTransaction(
                    from, to, amount, type, idempotencyKey);
        }
    }

    // CHAR(36) 멱등성 키 제한을 넘지 않도록 상품 구분과 가입 ID, 원금/이자 구분만 담는다.
    private String key(FinancialProductType type, Long enrollmentId, String part) {
        return (type == FinancialProductType.DEPOSIT ? "DPT_TERM:" : "SVG_TERM:")
                + enrollmentId + ":" + part;
    }

    private TransferType transferType(FinancialProductType type) {
        return type == FinancialProductType.DEPOSIT
                ? TransferType.DEPOSIT : TransferType.SAVING;
    }

    /** 외부 DTO와 실제 송금·점수 입력값을 같은 계산 결과로 묶는 내부 값 객체다. */
    private static class Calculation {
        private final long principal;
        private final long interest;
        private final TeenyScoreChangeRequestDTO scoreRequest;
        private final FinancialProductTerminationResponseDTO response;

        private Calculation(long principal, long interest,
                            TeenyScoreChangeRequestDTO scoreRequest,
                            FinancialProductTerminationResponseDTO response) {
            this.principal = principal;
            this.interest = interest;
            this.scoreRequest = scoreRequest;
            this.response = response;
        }

        private FinancialProductTerminationResponseDTO terminatedResponse() {
            return new FinancialProductTerminationResponseDTO(
                    response.getEnrollmentId(), response.getProductType(),
                    response.getProgressPercent(), response.getAppliedEarlyTerminationRate(),
                    response.getPrincipalAmount(), response.getInterestAmount(),
                    response.getScoreChange(), true);
        }
    }
}
