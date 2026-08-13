package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.*;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinancialProductApprovalServiceTest {
    private static final MemberPrincipal PARENT = new MemberPrincipal(1L, "PARENT");
    private FinancialProductMapper mapper;
    private FamilyAccessService familyAccessService;
    private TransferService transferService;
    private FinancialProductApprovalService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        familyAccessService = mock(FamilyAccessService.class);
        transferService = mock(TransferService.class);
        service = new FinancialProductApprovalService(mapper,
                familyAccessService,
                mock(WalletMapper.class), transferService,
                Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("부모 승인 시 가입 요청 시점에 저장한 예금 확정금리를 유지한다")
    void approveDepositKeepsRateFromRequestDate() {
        FinancialProductApprovalVO approval = approval(FinancialProductType.DEPOSIT);
        approval.setTransferId(30L);
        approval.setAppliedRate(new BigDecimal("4.20"));
        approval.setEarlyTerminationRate(new BigDecimal("0.50"));
        when(mapper.selectDepositApprovalForUpdate(1L, 7L)).thenReturn(approval);
        DepositProductVO product = new DepositProductVO();
        product.setId(3L);
        product.setRate12m(new BigDecimal("3.20"));
        product.setEarlyTerminationRate(new BigDecimal("0.50"));
        when(mapper.selectActiveDepositProductById(3L)).thenReturn(product);
        TransferVO transfer = new TransferVO();
        transfer.setId(30L);
        when(transferService.executeTransferAtomically(30L)).thenReturn(transfer);
        when(mapper.approveDepositEnrollment(eq(7L), eq(new BigDecimal("4.20")),
                eq(new BigDecimal("0.50")), any(), any())).thenReturn(1);

        service.approve(PARENT, "deposit", 7L);

        verify(familyAccessService).requireChildAccess(PARENT, 2L);
        verify(mapper).approveDepositEnrollment(eq(7L),
                eq(new BigDecimal("4.20")), eq(new BigDecimal("0.50")),
                eq(java.time.LocalDate.of(2026, 8, 11)),
                eq(java.time.LocalDate.of(2027, 8, 11)));
    }

    @Test
    @DisplayName("적금 승인 시 바로 출금하지 않고 다음 납입일을 시작일로 설정한다")
    void approveSavingSchedulesFirstPaymentWithoutImmediateTransfer() {
        FinancialProductApprovalVO approval = approval(FinancialProductType.SAVING);
        approval.setPaymentDay(25);
        approval.setAppliedRate(new BigDecimal("4.20"));
        approval.setEarlyTerminationRate(new BigDecimal("1.00"));
        when(mapper.selectSavingApprovalForUpdate(1L, 7L)).thenReturn(approval);
        SavingProductVO product = new SavingProductVO();
        product.setId(3L);
        product.setRate12m(new BigDecimal("3.20"));
        product.setEarlyTerminationRate(new BigDecimal("1.00"));
        when(mapper.selectActiveSavingProductById(3L)).thenReturn(product);
        when(mapper.approveSavingEnrollment(eq(7L), eq(new BigDecimal("4.20")),
                eq(new BigDecimal("1.00")), any(), any())).thenReturn(1);

        service.approve(PARENT, "saving", 7L);

        verifyNoInteractions(transferService);
        verify(mapper, never()).insertFirstSavingPayment(anyLong(), anyLong(), anyLong());
        verify(mapper).approveSavingEnrollment(eq(7L),
                eq(new BigDecimal("4.20")), eq(new BigDecimal("1.00")),
                eq(java.time.LocalDate.of(2026, 8, 25)),
                eq(java.time.LocalDate.of(2027, 8, 25)));
    }

    @Test
    @DisplayName("승인일과 납입일이 같으면 첫 납입일을 다음 달로 설정한다")
    void approvalOnPaymentDaySchedulesFirstPaymentNextMonth() {
        FinancialProductApprovalVO approval = approval(FinancialProductType.SAVING);
        approval.setPaymentDay(11);
        approval.setAppliedRate(new BigDecimal("4.20"));
        approval.setEarlyTerminationRate(new BigDecimal("1.00"));
        when(mapper.selectSavingApprovalForUpdate(1L, 7L)).thenReturn(approval);
        SavingProductVO product = new SavingProductVO();
        product.setId(3L);
        when(mapper.selectActiveSavingProductById(3L)).thenReturn(product);
        when(mapper.approveSavingEnrollment(eq(7L), eq(new BigDecimal("4.20")),
                eq(new BigDecimal("1.00")), any(), any())).thenReturn(1);

        service.approve(PARENT, "saving", 7L);

        verifyNoInteractions(transferService);
        verify(mapper).approveSavingEnrollment(eq(7L),
                eq(new BigDecimal("4.20")), eq(new BigDecimal("1.00")),
                eq(java.time.LocalDate.of(2026, 9, 11)),
                eq(java.time.LocalDate.of(2027, 9, 11)));
    }

    @Test
    @DisplayName("이미 처리된 계약은 다시 승인할 수 없다")
    void processedEnrollmentCannotBeApprovedAgain() {
        FinancialProductApprovalVO approval = approval(FinancialProductType.DEPOSIT);
        approval.setStatus("ACTIVE");
        when(mapper.selectDepositApprovalForUpdate(1L, 7L)).thenReturn(approval);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.approve(PARENT, "deposit", 7L));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_ENROLLMENT_NOT_PENDING,
                exception.getErrorCode());
        verify(transferService, never()).executeTransferAtomically(anyLong());
    }

    @Test
    @DisplayName("자녀 계정은 부모 승인 API를 사용할 수 없다")
    void childCannotApproveEnrollment() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.approve(new MemberPrincipal(2L, "CHILD"),
                        "deposit", 7L));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_PARENT_ONLY,
                exception.getErrorCode());
        verifyNoInteractions(familyAccessService);
    }

    @Test
    @DisplayName("부모 거절 시 예금의 대기 송금을 취소하고 계약을 REJECTED로 변경한다")
    void rejectDepositCancelsPendingTransfer() {
        FinancialProductApprovalVO approval = approval(FinancialProductType.DEPOSIT);
        approval.setTransferId(30L);
        when(mapper.selectDepositApprovalForUpdate(1L, 7L)).thenReturn(approval);
        when(mapper.rejectDepositEnrollment(7L)).thenReturn(1);

        service.reject(PARENT, "DEPOSIT", 7L);

        verify(transferService).cancelPendingTransfer(30L);
        verify(mapper).rejectDepositEnrollment(7L);
    }

    @Test
    @DisplayName("예금 거절 시 대기 송금이 없으면 계약을 REJECTED로 변경하지 않는다")
    void rejectDepositWithoutPendingTransferIsRejected() {
        FinancialProductApprovalVO approval = approval(FinancialProductType.DEPOSIT);
        approval.setTransferId(null);
        when(mapper.selectDepositApprovalForUpdate(1L, 7L)).thenReturn(approval);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reject(PARENT, "DEPOSIT", 7L));

        assertEquals(
                FinancialProductErrorCode.FINANCIAL_PRODUCT_PENDING_TRANSFER_NOT_FOUND,
                exception.getErrorCode());
        verify(transferService, never()).cancelPendingTransfer(anyLong());
        verify(mapper, never()).rejectDepositEnrollment(anyLong());
    }

    @Test
    @DisplayName("적금 거절은 대기 송금 없이 계약을 REJECTED로 변경한다")
    void rejectSavingWithoutPendingTransferChangesStatus() {
        FinancialProductApprovalVO approval = approval(FinancialProductType.SAVING);
        approval.setTransferId(null);
        when(mapper.selectSavingApprovalForUpdate(1L, 7L)).thenReturn(approval);
        when(mapper.rejectSavingEnrollment(7L)).thenReturn(1);

        service.reject(PARENT, "SAVING", 7L);

        verify(transferService, never()).cancelPendingTransfer(anyLong());
        verify(mapper).rejectSavingEnrollment(7L);
    }

    private FinancialProductApprovalVO approval(FinancialProductType type) {
        FinancialProductApprovalVO approval = new FinancialProductApprovalVO();
        approval.setEnrollmentId(7L);
        approval.setProductId(3L);
        approval.setProductType(type);
        approval.setParentId(1L);
        approval.setChildId(2L);
        approval.setRequestedAmount(50_000L);
        approval.setTermMonths(12);
        approval.setStatus("PENDING");
        return approval;
    }

    private FinancialProductBenefitVO benefit() {
        FinancialProductBenefitVO benefit = new FinancialProductBenefitVO();
        benefit.setChildId(2L);
        benefit.setGradeId(2L);
        benefit.setBonusRate(new BigDecimal("1.00"));
        benefit.setLoanRate(new BigDecimal("7.00"));
        return benefit;
    }
}
