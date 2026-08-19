package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductApprovalResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.*;
import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.service.WalletService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class FinancialProductApprovalService {
    private final FinancialProductMapper financialProductMapper;
    private final FamilyAccessService familyAccessService;
    private final WalletMapper walletMapper;
    private final TransferService transferService;
    private final WalletService walletService;
    private final Clock clock;
    private final NotificationService notificationService;

    public FinancialProductApprovalService(
            FinancialProductMapper financialProductMapper,
            FamilyAccessService familyAccessService,
            WalletMapper walletMapper,
            TransferService transferService,
            WalletService walletService,
            Clock clock,
            NotificationService notificationService) {
        this.financialProductMapper = financialProductMapper;
        this.familyAccessService = familyAccessService;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
        this.walletService = walletService;
        this.clock = clock;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<FinancialProductApprovalResponseDTO> getPendingApprovals(
            MemberPrincipal principal) {
        Long parentId = requireParent(principal);
        return financialProductMapper.selectPendingApprovalsByParentId(parentId)
                .stream().map(FinancialProductApprovalResponseDTO::of).toList();
    }

    @Transactional
    public FinancialProductApprovalResponseDTO getPendingApproval(
            MemberPrincipal principal, String productType, Long enrollmentId) {
        Long parentId = requireParent(principal);
        FinancialProductApprovalVO approval = approvalForUpdate(
                parentId, FinancialProductType.from(productType), enrollmentId);
        validateOwnedPending(principal, approval);
        return FinancialProductApprovalResponseDTO.of(approval);
    }

    @Transactional
    public void approve(MemberPrincipal principal, String productType,
                        Long enrollmentId) {
        Long parentId = requireParent(principal);
        FinancialProductType type = FinancialProductType.from(productType);
        FinancialProductApprovalVO approval = approvalForUpdate(
                parentId, type, enrollmentId);
        validateOwnedPending(principal, approval);
        LocalDate approvalDate = LocalDate.now(clock);
        int updated;
        switch (type) {
            case DEPOSIT -> {
                LocalDate startDate = approvalDate;
                LocalDate maturityDate = startDate.plusMonths(approval.getTermMonths());
                DepositProductVO product = financialProductMapper
                        .selectActiveDepositProductById(approval.getProductId());
                if (product == null) throw productNotFound();
                // 신청 단계에는 상품 지갑을 만들지 않고, 승인 트랜잭션 안에서만 생성·원금 이체한다.
                Long productWalletId = productWallet(approval, WalletType.DEPOSIT);
                executeDepositTransfer(approval, productWalletId);
                updated = financialProductMapper.approveDepositEnrollment(
                        enrollmentId, productWalletId, approval.getAppliedRate(),
                        approval.getEarlyTerminationRate(),
                        startDate, maturityDate);
            }
            case SAVING -> {
                // 가입일은 승인일로 보존하고, 만기는 최초 정기 납입 예정일부터 계산한다.
                LocalDate startDate = approvalDate;
                LocalDate firstPaymentDate = nextPaymentDate(
                        approvalDate, approval.getPaymentDay());
                LocalDate maturityDate = firstPaymentDate.plusMonths(
                        approval.getTermMonths());
                SavingProductVO product = financialProductMapper
                        .selectActiveSavingProductById(approval.getProductId());
                if (product == null) throw productNotFound();
                // 적금도 승인된 계약만 전용 지갑을 가져 거절·대기 신청의 빈 지갑이 남지 않는다.
                Long productWalletId = productWallet(approval, WalletType.SAVING);
                updated = financialProductMapper.approveSavingEnrollment(
                        enrollmentId, productWalletId, approval.getAppliedRate(),
                        approval.getEarlyTerminationRate(),
                        startDate, maturityDate);
            }
            case LOAN -> {
                LocalDate startDate = approvalDate;
                LocalDate maturityDate = startDate.plusMonths(approval.getTermMonths());
                LoanProductVO product = financialProductMapper
                        .selectActiveLoanProductById(approval.getProductId());
                if (product == null) throw productNotFound();
                FinancialProductBenefitVO benefit = financialProductMapper
                        .selectBenefitByChildId(approval.getChildId());
                if (benefit == null) throw notFound();
                validateLoanGrade(product, benefit);
                WalletVO parentWallet = memberWallet(parentId);
                WalletVO childWallet = memberWallet(approval.getChildId());
                TransferVO transfer = transferService.createPendingTransfer(
                        parentWallet.getId(), childWallet.getId(),
                        approval.getRequestedAmount(), TransferType.LOAN,
                        UUID.randomUUID().toString());
                transferService.executeTransferAtomically(transfer.getId());
                updated = financialProductMapper.approveLoanEnrollment(
                        enrollmentId, approval.getAppliedRate(),
                        approval.getLateFeeRate(),
                        startDate, maturityDate);
            }
            default -> throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_TYPE_INVALID);
        }
        if (updated != 1) throw notPending();
        notifyEnrollmentDecision(true, approval, type, enrollmentId);
    }

    @Transactional
    public void reject(MemberPrincipal principal, String productType,
                       Long enrollmentId) {
        Long parentId = requireParent(principal);
        FinancialProductType type = FinancialProductType.from(productType);
        FinancialProductApprovalVO approval = approvalForUpdate(
                parentId, type, enrollmentId);
        validateOwnedPending(principal, approval);
        if (type == FinancialProductType.DEPOSIT) {
            transferService.cancelPendingTransfer(approval.getTransferId());
        }
        int updated = switch (type) {
            case DEPOSIT -> financialProductMapper.rejectDepositEnrollment(enrollmentId);
            case SAVING -> financialProductMapper.rejectSavingEnrollment(enrollmentId);
            case LOAN -> financialProductMapper.rejectLoanEnrollment(enrollmentId);
        };
        if (updated != 1) throw notPending();
        notifyEnrollmentDecision(false, approval, type, enrollmentId);
    }

    /** 자녀에게 승인/거절 결과 알림을 보낸다. approve()/reject()와 같은 트랜잭션에 합류한다. */
    private void notifyEnrollmentDecision(
            boolean approved, FinancialProductApprovalVO approval,
            FinancialProductType type, Long enrollmentId) {
        String title = approved
                ? FinancialProductNotificationMessages.approvedTitle(type)
                : FinancialProductNotificationMessages.rejectedTitle(type);
        String content = approved
                ? FinancialProductNotificationMessages.approvedContent(
                        type, approval.getProductName(), approval.getRequestedAmount())
                : FinancialProductNotificationMessages.rejectedContent(approval.getProductName());
        notificationService.createNotification(
                approval.getChildId(), title, content,
                FinancialProductNotificationMessages.referenceType(type),
                enrollmentId, true);
    }

    private FinancialProductApprovalVO approvalForUpdate(
            Long parentId, FinancialProductType type, Long enrollmentId) {
        return switch (type) {
            case DEPOSIT -> financialProductMapper.selectDepositApprovalForUpdate(
                    parentId, enrollmentId);
            case SAVING -> financialProductMapper.selectSavingApprovalForUpdate(
                    parentId, enrollmentId);
            case LOAN -> financialProductMapper.selectLoanApprovalForUpdate(
                    parentId, enrollmentId);
        };
    }

    private void validateOwnedPending(MemberPrincipal principal,
                                      FinancialProductApprovalVO approval) {
        if (approval == null) throw notFound();
        familyAccessService.requireChildAccess(principal, approval.getChildId());
        if (!"PENDING".equals(approval.getStatus())) throw notPending();
    }

    private Long productWallet(FinancialProductApprovalVO approval, WalletType type) {
        // 재시도 시 이미 연결된 지갑을 재사용하여 상품 지갑이 중복 생성되지 않게 한다.
        return approval.getWalletId() != null
                ? approval.getWalletId()
                : walletService.createWallet(approval.getChildId(), type);
    }

    private TransferVO executeDepositTransfer(
            FinancialProductApprovalVO approval, Long productWalletId) {
        if (approval.getTransferId() != null) {
            return transferService.executeTransferAtomically(approval.getTransferId());
        }
        WalletVO childWallet = memberWallet(approval.getChildId());
        return transferService.transferInExistingTransaction(
                childWallet.getId(), productWalletId, approval.getRequestedAmount(),
                TransferType.DEPOSIT,
                "DEPOSIT_ENROLLMENT:" + approval.getEnrollmentId() + ":PRINCIPAL");
    }

    /*
     * 승인일 < 납입일이면 이번 달 납입일을 사용한다.
     * 승인일 >= 납입일이면 다음 달 납입일을 사용하므로,
     * 승인일과 납입일이 같아도 가입 직후 출금하지 않는다.
     */
    private LocalDate nextPaymentDate(LocalDate approvalDate, Integer paymentDay) {
        LocalDate paymentDate = approvalDate.withDayOfMonth(paymentDay);
        return paymentDate.isAfter(approvalDate)
                ? paymentDate
                : paymentDate.plusMonths(1);
    }

    private WalletVO memberWallet(Long memberId) {
        WalletVO wallet = walletMapper.selectMemberWalletByMemberId(memberId);
        if (wallet == null) throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        return wallet;
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

    private Long requireParent(MemberPrincipal principal) {
        if (principal == null || !"PARENT".equals(principal.role())) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_PARENT_ONLY);
        }
        return principal.memberId();
    }

    private BusinessException notFound() {
        return new BusinessException(
                FinancialProductErrorCode.FINANCIAL_PRODUCT_ENROLLMENT_NOT_FOUND);
    }

    private BusinessException productNotFound() {
        return new BusinessException(
                FinancialProductErrorCode.FINANCIAL_PRODUCT_NOT_FOUND);
    }

    private BusinessException notPending() {
        return new BusinessException(
                FinancialProductErrorCode.FINANCIAL_PRODUCT_ENROLLMENT_NOT_PENDING);
    }
}
