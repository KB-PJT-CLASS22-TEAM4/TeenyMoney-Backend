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
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.service.WalletService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class FinancialProductEnrollmentService {
    private final FinancialProductMapper financialProductMapper;
    private final FinancialProductRateCalculator rateCalculator;
    private final MemberMapper memberMapper;
    private final WalletMapper walletMapper;
    private final WalletService walletService;
    private final TransferService transferService;

    public FinancialProductEnrollmentService(
            FinancialProductMapper financialProductMapper,
            FinancialProductRateCalculator rateCalculator,
            MemberMapper memberMapper,
            WalletMapper walletMapper,
            WalletService walletService,
            TransferService transferService) {
        this.financialProductMapper = financialProductMapper;
        this.rateCalculator = rateCalculator;
        this.memberMapper = memberMapper;
        this.walletMapper = walletMapper;
        this.walletService = walletService;
        this.transferService = transferService;
    }

    @Transactional
    public FinancialProductEnrollmentRequestResponseDTO requestDeposit(
            MemberPrincipal principal, DepositEnrollmentRequestDTO request) {
        Long childId = requireChild(principal);
        Long parentId = activeParentId(childId);
        DepositProductVO product = financialProductMapper
                .selectActiveDepositProductById(request.getProductId());
        if (product == null) throw notFound();
        validateAmount(request.getAmount(), product.getMinAmount(), product.getMaxAmount());
        WalletVO memberWallet = lockMemberWallet(childId);
        ensureNoPending(financialProductMapper.countPendingDepositEnrollment(
                childId, product.getId()));
        FinancialProductBenefitVO benefit = benefit(childId);
        BigDecimal rate = rateCalculator.depositRate(product,
                request.getTermMonths(), benefit.getBonusRate());
        validateBalance(memberWallet, request.getAmount());
        Long productWalletId = walletService.createWallet(childId, WalletType.DEPOSIT);

        FinancialProductEnrollmentCommandVO command = baseCommand(
                product.getId(), parentId, childId, productWalletId, rate,
                request.getTermMonths());
        command.setAppliedEarlyTerminationRate(product.getEarlyTerminationRate());
        financialProductMapper.insertDepositEnrollment(command);
        transferService.createPendingTransfer(memberWallet.getId(), productWalletId,
                request.getAmount(), TransferType.DEPOSIT, UUID.randomUUID().toString());
        return response(command.getId(), FinancialProductType.DEPOSIT, rate);
    }

    @Transactional
    public FinancialProductEnrollmentRequestResponseDTO requestSaving(
            MemberPrincipal principal, SavingEnrollmentRequestDTO request) {
        Long childId = requireChild(principal);
        Long parentId = activeParentId(childId);
        SavingProductVO product = financialProductMapper
                .selectActiveSavingProductById(request.getProductId());
        if (product == null) throw notFound();
        validateAmount(request.getMonthlyAmount(), product.getMinMonthAmount(),
                product.getMaxMonthAmount());
        lockMemberWallet(childId);
        ensureNoPending(financialProductMapper.countPendingSavingEnrollment(
                childId, product.getId()));
        FinancialProductBenefitVO benefit = benefit(childId);
        BigDecimal rate = rateCalculator.savingRate(product,
                request.getTermMonths(), benefit.getBonusRate());
        Long productWalletId = walletService.createWallet(childId, WalletType.SAVING);

        FinancialProductEnrollmentCommandVO command = baseCommand(
                product.getId(), parentId, childId, productWalletId, rate,
                request.getTermMonths());
        command.setAppliedEarlyTerminationRate(product.getEarlyTerminationRate());
        command.setAmount(request.getMonthlyAmount());
        command.setPaymentDay(request.getPaymentDay());
        command.setAutoTransfer(request.getAutoTransfer());
        financialProductMapper.insertSavingEnrollment(command);
        return response(command.getId(), FinancialProductType.SAVING, rate);
    }

    @Transactional
    public FinancialProductEnrollmentRequestResponseDTO requestLoan(
            MemberPrincipal principal, LoanEnrollmentRequestDTO request) {
        Long childId = requireChild(principal);
        Long parentId = activeParentId(childId);
        LoanProductVO product = financialProductMapper
                .selectActiveLoanProductById(request.getProductId());
        if (product == null) throw notFound();
        validateAmount(request.getPrincipalAmount(), product.getMinAmount(),
                product.getMaxAmount());
        if (!ListTerms.LOAN.contains(request.getTermMonths())) {
            throw new BusinessException(FinancialProductErrorCode.FINANCIAL_PRODUCT_INVALID_TERM);
        }
        lockMemberWallet(childId);
        ensureNoPending(financialProductMapper.countPendingLoanEnrollment(
                childId, product.getId()));
        FinancialProductBenefitVO benefit = benefit(childId);
        validateLoanGrade(product, benefit);
        FinancialProductEnrollmentCommandVO command = baseCommand(
                product.getId(), parentId, childId, null, product.getBaseRate(),
                request.getTermMonths());
        command.setAppliedLateFeeRate(product.getLateFeeRate());
        command.setAmount(request.getPrincipalAmount());
        command.setPaymentDay(request.getPaymentDay());
        command.setAutoTransfer(request.getAutoTransfer());
        financialProductMapper.insertLoanEnrollment(command);
        return response(command.getId(), FinancialProductType.LOAN,
                product.getBaseRate());
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
}
