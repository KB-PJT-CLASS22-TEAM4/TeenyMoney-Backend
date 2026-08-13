package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingPaymentDueVO;
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

import java.time.LocalDate;

@Service
public class SavingAutoPaymentProcessor {
    private final FinancialProductMapper financialProductMapper;
    private final WalletMapper walletMapper;
    private final TransferService transferService;

    public SavingAutoPaymentProcessor(
            FinancialProductMapper financialProductMapper,
            WalletMapper walletMapper,
            TransferService transferService) {
        this.financialProductMapper = financialProductMapper;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long enrollmentId, LocalDate paymentDate) {
        SavingPaymentDueVO payment = financialProductMapper
                .selectDueSavingPaymentForUpdate(enrollmentId, paymentDate);
        if (payment == null || financialProductMapper.countSavingPaymentHistory(
                enrollmentId, payment.getInstallmentNo()) > 0) {
            return;
        }

        WalletVO memberWallet = walletMapper.selectMemberWalletByMemberId(
                payment.getChildId());
        if (memberWallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }
        WalletVO lockedWallet = walletMapper.selectWalletForUpdate(memberWallet.getId());
        if (lockedWallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        if (lockedWallet.getBalance() < payment.getMonthlyAmount()) {
            financialProductMapper.insertSavingPaymentHistory(
                    enrollmentId, null, payment.getInstallmentNo(),
                    payment.getMonthlyAmount(), 0L, "MISSED");
            return;
        }

        String idempotencyKey = "SAVING:" + enrollmentId + ":"
                + payment.getInstallmentNo();
        TransferVO transfer = transferService.createPendingTransfer(
                lockedWallet.getId(), payment.getProductWalletId(),
                payment.getMonthlyAmount(), TransferType.SAVING, idempotencyKey);
        transferService.executeTransferAtomically(transfer.getId());
        financialProductMapper.insertSavingPaymentHistory(
                enrollmentId, transfer.getId(), payment.getInstallmentNo(),
                payment.getMonthlyAmount(), payment.getMonthlyAmount(), "PAID");
    }
}
