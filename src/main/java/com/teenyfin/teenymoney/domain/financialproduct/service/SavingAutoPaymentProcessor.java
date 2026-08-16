package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingPaymentDueVO;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** 정기적금 한 계약의 한 회차를 별도 트랜잭션으로 자동납입 처리한다. */
@Service
public class SavingAutoPaymentProcessor {
    private final FinancialProductMapper financialProductMapper;
    private final WalletMapper walletMapper;
    private final TransferService transferService;
    private final TeenyScorePolicyService scorePolicyService;
    private final TeenyScoreChangeService scoreChangeService;

    public SavingAutoPaymentProcessor(
            FinancialProductMapper financialProductMapper,
            WalletMapper walletMapper,
            TransferService transferService,
            TeenyScorePolicyService scorePolicyService,
            TeenyScoreChangeService scoreChangeService) {
        this.financialProductMapper = financialProductMapper;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
        this.scorePolicyService = scorePolicyService;
        this.scoreChangeService = scoreChangeService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long enrollmentId, LocalDate paymentDate) {
        // 가입 행을 잠그고 회차 이력을 확인하여 스케줄러 재실행과 동시 실행을 모두 차단한다.
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
            // 잔액 부족도 처리 결과로 기록해야 같은 회차를 재실행할 때 감점이 반복되지 않는다.
            financialProductMapper.insertSavingPaymentHistory(
                    enrollmentId, null, payment.getInstallmentNo(),
                    payment.getMonthlyAmount(), 0L, "MISSED");
            scoreChangeService.change(scorePolicyService.fixedSavingInstallment(
                    payment.getChildId(), enrollmentId,
                    payment.getInstallmentNo(), false));
            return;
        }

        // 계약과 회차로 만든 고정 키는 이력 저장 직전 장애가 나도 중복 출금을 막는 마지막 방어선이다.
        String idempotencyKey = "SAVING:" + enrollmentId + ":"
                + payment.getInstallmentNo();
        TransferVO transfer = transferService.createPendingTransfer(
                lockedWallet.getId(), payment.getProductWalletId(),
                payment.getMonthlyAmount(), TransferType.SAVING, idempotencyKey);
        transferService.executeTransferAtomically(transfer.getId());
        financialProductMapper.insertSavingPaymentHistory(
                enrollmentId, transfer.getId(), payment.getInstallmentNo(),
                payment.getMonthlyAmount(), payment.getMonthlyAmount(), "PAID");
        scoreChangeService.change(scorePolicyService.fixedSavingInstallment(
                payment.getChildId(), enrollmentId,
                payment.getInstallmentNo(), true));
    }
}
