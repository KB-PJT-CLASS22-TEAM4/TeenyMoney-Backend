package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingPaymentDueVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SavingAutoPaymentProcessorTest {
    private FinancialProductMapper mapper;
    private WalletMapper walletMapper;
    private TransferService transferService;
    private NotificationService notificationService;
    private SavingAutoPaymentProcessor processor;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        walletMapper = mock(WalletMapper.class);
        transferService = mock(TransferService.class);
        notificationService = mock(NotificationService.class);
        processor = new SavingAutoPaymentProcessor(
                mapper, walletMapper, transferService,
                mock(TeenyScorePolicyService.class),
                mock(TeenyScoreChangeService.class),
                notificationService);
    }

    @Test
    @DisplayName("지정 납입일에 자녀 지갑에서 적금 지갑으로 송금하고 PAID 이력을 생성한다")
    void duePaymentTransfersMoneyAndCreatesPaidHistory() {
        LocalDate paymentDate = LocalDate.of(2026, 8, 25);
        when(mapper.selectDueSavingPaymentForUpdate(7L, paymentDate))
                .thenReturn(duePayment());
        when(mapper.countSavingPaymentHistory(7L, 1)).thenReturn(0);
        when(walletMapper.selectMemberWalletByMemberId(2L))
                .thenReturn(wallet(10L, 100_000L));
        when(walletMapper.selectWalletForUpdate(10L))
                .thenReturn(wallet(10L, 100_000L));
        TransferVO transfer = new TransferVO();
        transfer.setId(30L);
        when(transferService.createPendingTransfer(
                eq(10L), eq(20L), eq(30_000L), any(), anyString()))
                .thenReturn(transfer);

        processor.process(7L, paymentDate);

        verify(transferService).executeTransferAtomically(30L);
        verify(mapper).insertSavingPaymentHistory(
                7L, 30L, 1, 30_000L, 30_000L, "PAID");
        verify(notificationService).createNotification(
                eq(2L), eq("적금이 납입됐어요"), anyString(),
                eq(NotificationReferenceType.SAVING_PAYMENT), eq(7L), eq(true));
    }

    @Test
    @DisplayName("지정 납입일에 잔액이 부족하면 송금 없이 MISSED 이력을 생성한다")
    void insufficientBalanceCreatesMissedHistory() {
        LocalDate paymentDate = LocalDate.of(2026, 8, 25);
        when(mapper.selectDueSavingPaymentForUpdate(7L, paymentDate))
                .thenReturn(duePayment());
        when(mapper.countSavingPaymentHistory(7L, 1)).thenReturn(0);
        when(walletMapper.selectMemberWalletByMemberId(2L))
                .thenReturn(wallet(10L, 20_000L));
        when(walletMapper.selectWalletForUpdate(10L))
                .thenReturn(wallet(10L, 20_000L));

        processor.process(7L, paymentDate);

        verifyNoInteractions(transferService);
        verify(mapper).insertSavingPaymentHistory(
                7L, null, 1, 30_000L, 0L, "MISSED");
        // 실패 원인이 자녀 지갑 잔액이므로 부모가 아닌 자녀에게만 한 건 발송한다.
        verify(notificationService).createNotification(
                eq(2L), eq("적금 자동납입에 실패했어요"), anyString(),
                eq(NotificationReferenceType.SAVING_PAYMENT), eq(7L), eq(true));
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    @DisplayName("이미 처리한 회차는 스케줄러를 재실행해도 중복 송금하지 않는다")
    void existingPaymentHistoryPreventsDuplicateTransfer() {
        LocalDate paymentDate = LocalDate.of(2026, 8, 25);
        when(mapper.selectDueSavingPaymentForUpdate(7L, paymentDate))
                .thenReturn(duePayment());
        when(mapper.countSavingPaymentHistory(7L, 1)).thenReturn(1);

        processor.process(7L, paymentDate);

        verifyNoInteractions(walletMapper, transferService);
        verify(mapper, never()).insertSavingPaymentHistory(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("자유적금은 정액적금 자동납입 처리에서 제외한다")
    void freeSavingWithoutPaymentCreatesMissedHistory() {
        LocalDate paymentDate = LocalDate.of(2026, 8, 23);
        SavingPaymentDueVO payment = duePayment();
        payment.setSavingsType("FREE");
        when(mapper.selectDueSavingPaymentForUpdate(7L, paymentDate))
                .thenReturn(payment);
        when(mapper.countSavingPaymentHistory(7L, 1)).thenReturn(0);

        processor.process(7L, paymentDate);

        verifyNoInteractions(walletMapper, transferService);
        verify(mapper, never()).insertSavingPaymentHistory(
                any(), any(), any(), any(), any(), any());
    }

    private SavingPaymentDueVO duePayment() {
        SavingPaymentDueVO payment = new SavingPaymentDueVO();
        payment.setEnrollmentId(7L);
        payment.setChildId(2L);
        payment.setProductWalletId(20L);
        payment.setMonthlyAmount(30_000L);
        payment.setInstallmentNo(1);
        payment.setSavingsType("FIXED");
        return payment;
    }

    private WalletVO wallet(Long id, Long balance) {
        WalletVO wallet = new WalletVO();
        wallet.setId(id);
        wallet.setBalance(balance);
        return wallet;
    }
}
