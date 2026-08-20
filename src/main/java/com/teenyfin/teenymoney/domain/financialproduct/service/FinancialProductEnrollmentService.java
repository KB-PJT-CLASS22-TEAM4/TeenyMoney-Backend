package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.request.DepositEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.LoanEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.SavingEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductEnrollmentRequestResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.*;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class FinancialProductEnrollmentService {
    private final FinancialProductMapper financialProductMapper;
    private final FinancialProductRateCalculator rateCalculator;
    private final MemberMapper memberMapper;
    private final WalletMapper walletMapper;
    private final NotificationService notificationService;

    public FinancialProductEnrollmentService(
            FinancialProductMapper financialProductMapper,
            FinancialProductRateCalculator rateCalculator,
            MemberMapper memberMapper,
            WalletMapper walletMapper,
            NotificationService notificationService) {
        this.financialProductMapper = financialProductMapper;
        this.rateCalculator = rateCalculator;
        this.memberMapper = memberMapper;
        this.walletMapper = walletMapper;
        this.notificationService = notificationService;
    }

    @Transactional
    public FinancialProductEnrollmentRequestResponseDTO requestDeposit(
            MemberPrincipal principal, DepositEnrollmentRequestDTO request) {
        Long childId = requireChild(principal);
        Long parentId = activeParentId(childId);
        DepositProductVO product = financialProductMapper
                .selectVisibleDepositProductById(request.getProductId(), childId);
        if (product == null) throw notFound();
        validateAmount(request.getAmount(), product.getMinAmount(), product.getMaxAmount());
        WalletVO memberWallet = lockMemberWallet(childId);
        ensureNoPending(financialProductMapper.countPendingDepositEnrollment(
                childId, product.getId()));
        FinancialProductBenefitVO benefit = benefit(childId);
        // 부모 생성 예금도 일반 예금과 동일하게 월간 적용 등급 우대금리를 더한다.
        BigDecimal rate = rateCalculator.depositRate(product,
                request.getTermMonths(), benefit.getBonusRate());
        // 중도해지는 우대금리를 제외한 가입 당시 약정 기본금리에 진행률 비율을 적용한다.
        BigDecimal baseRate = rateCalculator.depositRate(
                product, request.getTermMonths(), BigDecimal.ZERO);
        validateBalance(memberWallet, request.getAmount());
        FinancialProductEnrollmentCommandVO command = baseCommand(
                product.getId(), parentId, childId, null, rate,
                request.getTermMonths());
        // 예전에는 PENDING 송금의 amount가 신청 금액 저장소 역할을 했다.
        // 승인 전에는 상품 지갑과 송금을 만들지 않으므로, 신청 금액을 계약 행에 스냅샷으로 남긴다.
        command.setAmount(request.getAmount());
        command.setAppliedEarlyTerminationRate(baseRate);
        financialProductMapper.insertDepositEnrollment(command);
        notifyEnrollmentRequested(parentId, childId, FinancialProductType.DEPOSIT,
                product.getName(), request.getAmount(), command.getId());
        return response(command.getId(), FinancialProductType.DEPOSIT, rate);
    }

    @Transactional
    public FinancialProductEnrollmentRequestResponseDTO requestSaving(
            MemberPrincipal principal, SavingEnrollmentRequestDTO request) {
        Long childId = requireChild(principal);
        Long parentId = activeParentId(childId);
        SavingProductVO product = financialProductMapper
                .selectVisibleSavingProductById(request.getProductId(), childId);
        if (product == null) throw notFound();
        validateAmount(request.getMonthlyAmount(), product.getMinMonthAmount(),
                product.getMaxMonthAmount());
        lockMemberWallet(childId);
        ensureNoPending(financialProductMapper.countPendingSavingEnrollment(
                childId, product.getId()));
        FinancialProductBenefitVO benefit = benefit(childId);
        // 부모 생성 적금도 일반 적금과 동일하게 월간 적용 등급 우대금리를 더한다.
        BigDecimal rate = rateCalculator.savingRate(product,
                request.getTermMonths(), benefit.getBonusRate());
        // appliedRate에는 우대금리를 포함하되, 중도해지 계산용 스냅샷에는 기본금리만 보관한다.
        BigDecimal baseRate = rateCalculator.savingRate(
                product, request.getTermMonths(), BigDecimal.ZERO);
        FinancialProductEnrollmentCommandVO command = baseCommand(
                product.getId(), parentId, childId, null, rate,
                request.getTermMonths());
        command.setAppliedEarlyTerminationRate(baseRate);
        command.setAmount(request.getMonthlyAmount());
        command.setPaymentDay(request.getPaymentDay());
        command.setAutoTransfer(request.getAutoTransfer());
        financialProductMapper.insertSavingEnrollment(command);
        notifyEnrollmentRequested(parentId, childId, FinancialProductType.SAVING,
                product.getName(), request.getMonthlyAmount(), command.getId());
        return response(command.getId(), FinancialProductType.SAVING, rate);
    }

    @Transactional
    public FinancialProductEnrollmentRequestResponseDTO requestLoan(
            MemberPrincipal principal, LoanEnrollmentRequestDTO request) {
        Long childId = requireChild(principal);
        Long parentId = activeParentId(childId);
        LoanProductVO product = financialProductMapper
                .selectVisibleLoanProductById(request.getProductId(), childId);
        if (product == null) throw notFound();
        validateAmount(request.getPrincipalAmount(), product.getMinAmount(),
                product.getMaxAmount());
        if (!ListTerms.LOAN.contains(request.getTermMonths())) {
            throw new BusinessException(FinancialProductErrorCode.FINANCIAL_PRODUCT_INVALID_TERM);
        }
        if (product.getProductSource() == FinancialProductSource.PARENT
                && !loanTerms(product).contains(request.getTermMonths())) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_INVALID_TERM);
        }
        lockMemberWallet(childId);
        ensureNoPending(financialProductMapper.countPendingLoanEnrollment(
                childId, product.getId()));
        FinancialProductBenefitVO benefit = benefit(childId);
        // 자녀 등급은 대출 가입 가능 여부만 판단하며 계약 금리를 변경하지 않는다.
        validateLoanGrade(product, benefit);
        // 부모 생성 대출도 부모가 상품에 입력한 baseRate를 가입 시점 확정금리로 저장한다.
        BigDecimal rate = loanRate(product);
        FinancialProductEnrollmentCommandVO command = baseCommand(
                product.getId(), parentId, childId, null, rate,
                request.getTermMonths());
        command.setAppliedLateFeeRate(product.getLateFeeRate());
        command.setAmount(request.getPrincipalAmount());
        command.setPaymentDay(request.getPaymentDay());
        command.setAutoTransfer(request.getAutoTransfer());
        financialProductMapper.insertLoanEnrollment(command);
        notifyEnrollmentRequested(parentId, childId, FinancialProductType.LOAN,
                product.getName(), request.getPrincipalAmount(), command.getId());
        return response(command.getId(), FinancialProductType.LOAN, rate);
    }

    /** 자녀가 본인의 승인 대기(PENDING) 신청을 취소한다. */
    @Transactional
    public FinancialProductEnrollmentRequestResponseDTO cancel(
            MemberPrincipal principal, String productType, Long enrollmentId) {
        Long childId = requireChild(principal);
        FinancialProductType type = FinancialProductType.from(productType);
        int updated = switch (type) {
            case DEPOSIT -> financialProductMapper.cancelDepositEnrollment(childId, enrollmentId);
            case SAVING -> financialProductMapper.cancelSavingEnrollment(childId, enrollmentId);
            case LOAN -> financialProductMapper.cancelLoanEnrollment(childId, enrollmentId);
        };
        if (updated != 1) throw notCancelable(type, childId, enrollmentId);
        return FinancialProductEnrollmentRequestResponseDTO.builder()
                .enrollmentId(enrollmentId).productType(type).status("CANCELED").build();
    }

    /** 조건에 안 걸린 이유가 "없음"인지 "PENDING이 아님"인지 구분해 알맞은 에러를 던진다. */
    private BusinessException notCancelable(
            FinancialProductType type, Long childId, Long enrollmentId) {
        boolean exists = switch (type) {
            case DEPOSIT -> financialProductMapper
                    .selectDepositEnrollmentByChildIdAndId(childId, enrollmentId) != null;
            case SAVING -> financialProductMapper
                    .selectSavingEnrollmentByChildIdAndId(childId, enrollmentId) != null;
            case LOAN -> financialProductMapper
                    .selectLoanEnrollmentByChildIdAndId(childId, enrollmentId) != null;
        };
        return exists
                ? new BusinessException(FinancialProductErrorCode.FINANCIAL_PRODUCT_ENROLLMENT_NOT_PENDING)
                : notFound();
    }

    /**
     * 부모에게 가입 요청 알림을 보낸다. NotificationService.createNotification()도 @Transactional이라
     * 이 메서드와 같은 트랜잭션에 합류하므로, 인앱 알림 이력 저장은 가입 요청과 원자적으로 묶인다.
     * FCM 푸시 발송 실패는 FcmService 내부에서 로그만 남기고 삼켜지므로 가입 요청에 영향을 주지 않는다.
     */
    private void notifyEnrollmentRequested(
            Long parentId, Long childId, FinancialProductType type,
            String productName, long amount, Long enrollmentId) {
        String childName = memberMapper.selectById(childId).getName();
        notificationService.createNotification(
                parentId,
                FinancialProductNotificationMessages.requestTitle(type, childName),
                FinancialProductNotificationMessages.requestContent(type, productName, amount),
                FinancialProductNotificationMessages.referenceType(type),
                enrollmentId,
                true);
    }

    private Long requireChild(MemberPrincipal principal) {
        if (principal == null || !"CHILD".equals(principal.role())) {
            throw new BusinessException(FinancialProductErrorCode.FINANCIAL_PRODUCT_CHILD_ONLY);
        }
        return principal.memberId();
    }

    private Long activeParentId(Long childId) {
        MemberParentVO parent = memberMapper.selectActiveParentByChildId(childId);
        if (parent == null) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_PARENT_NOT_CONNECTED);
        }
        return parent.getParentId();
    }

    private FinancialProductBenefitVO benefit(Long childId) {
        FinancialProductBenefitVO benefit =
                financialProductMapper.selectBenefitByChildId(childId);
        if (benefit == null) throw notFound();
        return benefit;
    }

    private WalletVO lockMemberWallet(Long childId) {
        WalletVO wallet = walletMapper.selectMemberWalletByMemberId(childId);
        if (wallet == null) throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        return walletMapper.selectWalletForUpdate(wallet.getId());
    }

    private void validateLoanGrade(LoanProductVO product,
                                   FinancialProductBenefitVO benefit) {
        if (benefit.getLoanRate() == null) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_LOAN_GRADE_RESTRICTED);
        }
        if (benefit.getGradeId() == null || product.getRequiredGradeId() == null
                || benefit.getGradeId() < product.getRequiredGradeId()) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_INSUFFICIENT_GRADE);
        }
    }

    /** 부모가 설정한 상품 금리를 포함해 상품의 기본금리를 계약 확정금리로 저장한다. */
    private BigDecimal loanRate(LoanProductVO product) {
        // benefit.loanRate를 사용하면 자녀 등급에 따라 부모 상품의 금리가 바뀌므로 사용하지 않는다.
        return product.getBaseRate();
    }

    private void validateAmount(Long amount, Long minimum, Long maximum) {
        if (amount == null || (minimum != null && amount < minimum)
                || (maximum != null && amount > maximum)) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_INVALID_AMOUNT);
        }
    }

    private void validateBalance(WalletVO wallet, Long amount) {
        if (wallet.getBalance() < amount) {
            throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    private void ensureNoPending(int count) {
        if (count > 0) throw new BusinessException(
                FinancialProductErrorCode.FINANCIAL_PRODUCT_ENROLLMENT_DUPLICATED);
    }

    private FinancialProductEnrollmentCommandVO baseCommand(
            Long productId, Long parentId, Long childId, Long walletId,
            BigDecimal rate, Integer termMonths) {
        FinancialProductEnrollmentCommandVO command =
                new FinancialProductEnrollmentCommandVO();
        command.setProductId(productId);
        command.setParentId(parentId);
        command.setChildId(childId);
        command.setWalletId(walletId);
        command.setAppliedRate(rate);
        command.setTermMonths(termMonths);
        return command;
    }

    private FinancialProductEnrollmentRequestResponseDTO response(
            Long id, FinancialProductType type, BigDecimal rate) {
        return FinancialProductEnrollmentRequestResponseDTO.builder()
                .enrollmentId(id).productType(type).status("PENDING")
                .expectedAppliedRate(rate).build();
    }

    private BusinessException notFound() {
        return new BusinessException(FinancialProductErrorCode.FINANCIAL_PRODUCT_NOT_FOUND);
    }

    private static final class ListTerms {
        private static final java.util.Set<Integer> LOAN =
                java.util.Set.of(1, 3, 6, 12);
    }

    private java.util.Set<Integer> loanTerms(LoanProductVO product) {
        java.util.Set<Integer> terms = new java.util.HashSet<>();
        if (Boolean.TRUE.equals(product.getAvailable1m())) terms.add(1);
        if (Boolean.TRUE.equals(product.getAvailable3m())) terms.add(3);
        if (Boolean.TRUE.equals(product.getAvailable6m())) terms.add(6);
        if (Boolean.TRUE.equals(product.getAvailable12m())) terms.add(12);
        return terms.isEmpty() && product.getProductSource() != FinancialProductSource.PARENT
                ? ListTerms.LOAN : terms;
    }
}
