package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductApprovalResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.*;
import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
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
    private final Clock clock;

    public FinancialProductApprovalService(
            FinancialProductMapper financialProductMapper,
            FamilyAccessService familyAccessService,
            WalletMapper walletMapper,
            TransferService transferService,
            Clock clock) {
        this.financialProductMapper = financialProductMapper;
        this.familyAccessService = familyAccessService;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
        this.clock = clock;
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
                executeExistingTransfer(approval);
                updated = financialProductMapper.approveDepositEnrollment(
                        enrollmentId, approval.getAppliedRate(),
                        approval.getEarlyTerminationRate(),
                        startDate, maturityDate);
            }
            case SAVING -> {
                LocalDate startDate = nextPaymentDate(
                        approvalDate, approval.getPaymentDay());
                LocalDate maturityDate = startDate.plusMonths(
                        approval.getTermMonths());
                SavingProductVO product = financialProductMapper
                        .selectActiveSavingProductById(approval.getProductId());
                if (product == null) throw productNotFound();
                updated = financialProductMapper.approveSavingEnrollment(
                        enrollmentId, approval.getAppliedRate(),
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
            if (approval.getTransferId() == null) {
                throw new BusinessException(
                        FinancialProductErrorCode
                                .FINANCIAL_PRODUCT_PENDING_TRANSFER_NOT_FOUND
                );
            }

            transferService.cancelPendingTransfer(approval.getTransferId());
        }
        int updated = switch (type) {
            case DEPOSIT -> financialProductMapper.rejectDepositEnrollment(enrollmentId);
            case SAVING -> financialProductMapper.rejectSavingEnrollment(enrollmentId);
            case LOAN -> financialProductMapper.rejectLoanEnrollment(enrollmentId);
        };
        if (updated != 1) throw notPending();
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

    private TransferVO executeExistingTransfer(
            FinancialProductApprovalVO approval) {
        if (approval.getTransferId() == null) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_PENDING_TRANSFER_NOT_FOUND);
        }
        return transferService.executeTransferAtomically(approval.getTransferId());
    }

    private LocalDate nextPaymentDate(LocalDate approvalDate, Integer paymentDay) {
        LocalDate paymentDate = approvalDate.withDayOfMonth(paymentDay);
        return paymentDate.isBefore(approvalDate)
                ? paymentDate.plusMonths(1)
                : paymentDate;
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
