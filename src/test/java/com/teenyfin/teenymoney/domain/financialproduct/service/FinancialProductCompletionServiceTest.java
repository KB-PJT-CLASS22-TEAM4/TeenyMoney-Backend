package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductCompletionDetailResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductEnrollmentVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductSettlementVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanRepaymentHistoryVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingPaymentHistoryVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialProductCompletionServiceTest {
    private FinancialProductMapper mapper;
    private FamilyAccessService familyAccessService;
    private FinancialProductCompletionService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        familyAccessService = mock(FamilyAccessService.class);
        service = new FinancialProductCompletionService(
                mapper, familyAccessService, new FinancialProductInterestCalculator());
    }

    @Test
    @DisplayName("만기 예금은 실제 정산 송금의 원금과 이자를 반환한다")
    void 완료된_예금은_실제_정산_원금과_이자를_반환한다() {
        FinancialProductEnrollmentVO enrollment = enrollment(
                FinancialProductType.DEPOSIT, "MATURED");
        FinancialProductSettlementVO settlement = settlement(1_000_000L, 22_500L);
        when(mapper.selectDepositEnrollmentByChildIdAndId(2L, 10L)).thenReturn(enrollment);
        when(mapper.selectCompletedSettlement("DPT_MAT:10"))
                .thenReturn(settlement);

        FinancialProductCompletionDetailResponseDTO result = service.getMyCompletionDetail(
                new MemberPrincipal(2L, "CHILD"), "deposit", 10L);

        assertEquals(1_000_000L, result.getPrincipalAmount());
        assertEquals(22_500L, result.getInterestAmount());
        assertEquals(1_022_500L, result.getTotalAmount());
        assertEquals(6, result.getDepositPeriods().size());
        assertEquals(22_500L, result.getDepositPeriods().get(5).getCumulativeInterestAmount());
    }

    @Test
    @DisplayName("적금 회차별 이자 합계는 실제 만기 이자 송금과 일치한다")
    void 적금_회차별_이자의_합은_실제_정산_이자와_일치한다() {
        FinancialProductEnrollmentVO enrollment = enrollment(
                FinancialProductType.SAVING, "MATURED");
        SavingPaymentHistoryVO first = savingHistory(1, 100_000L, "PAID", "2026-01-01T10:00:00");
        SavingPaymentHistoryVO missed = savingHistory(2, 0L, "MISSED", null);
        when(mapper.selectSavingEnrollmentByChildIdAndId(2L, 10L)).thenReturn(enrollment);
        when(mapper.selectCompletedSettlement("SVG_MAT:10"))
                .thenReturn(settlement(100_000L, 2_250L));
        when(mapper.selectSavingPaymentHistories(10L)).thenReturn(List.of(first, missed));

        FinancialProductCompletionDetailResponseDTO result = service.getMyCompletionDetail(
                new MemberPrincipal(2L, "CHILD"), "saving", 10L);

        assertEquals(2, result.getSavingPayments().size());
        assertEquals("MISSED", result.getSavingPayments().get(1).getStatus());
        assertEquals(2_250L, result.getSavingPayments().stream()
                .mapToLong(payment -> payment.getInterestAmount()).sum());
    }

    @Test
    @DisplayName("완납 대출은 정규상환과 조기상환을 구분해 실제 납부 합계를 반환한다")
    void 완납_대출은_정규상환과_조기상환을_구분하고_실제_납부합을_반환한다() {
        FinancialProductEnrollmentVO enrollment = enrollment(
                FinancialProductType.LOAN, "REPAID");
        enrollment.setPrincipalAmount(600_000L);
        enrollment.setPaymentDay(10);
        LoanRepaymentHistoryVO scheduled = loanHistory(1, "SCHEDULED", 100_000L, 2_250L);
        LoanRepaymentHistoryVO early = loanHistory(0, "EARLY", 500_000L, 0L);
        when(mapper.selectLoanEnrollmentByChildIdAndId(2L, 10L)).thenReturn(enrollment);
        when(mapper.selectLoanRepaymentHistories(10L)).thenReturn(List.of(scheduled, early));

        FinancialProductCompletionDetailResponseDTO result = service.getMyCompletionDetail(
                new MemberPrincipal(2L, "CHILD"), "loan", 10L);

        assertEquals("EARLY_REPAID", result.getCompletionType());
        assertEquals(600_000L, result.getPrincipalAmount());
        assertEquals(2_250L, result.getInterestAmount());
        assertEquals("EARLY", result.getLoanRepayments().get(1).getRepaymentType());
        assertNull(result.getLoanRepayments().get(1).getInstallmentNo());
    }

    @Test
    @DisplayName("진행 중인 계약은 완료 상세 조회 대상이 아니다")
    void 진행중인_계약은_완료_상세를_조회할_수_없다() {
        when(mapper.selectDepositEnrollmentByChildIdAndId(2L, 10L))
                .thenReturn(enrollment(FinancialProductType.DEPOSIT, "ACTIVE"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getMyCompletionDetail(
                        new MemberPrincipal(2L, "CHILD"), "deposit", 10L));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_COMPLETION_DETAIL_NOT_AVAILABLE,
                exception.getErrorCode());
    }

    @Test
    @DisplayName("중도해지한 예·적금은 만기 상세 조회 대상에서 제외한다")
    void 중도해지한_예적금은_완료_상세_조회에서_제외한다() {
        when(mapper.selectSavingEnrollmentByChildIdAndId(2L, 10L))
                .thenReturn(enrollment(FinancialProductType.SAVING, "TERMINATED"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getMyCompletionDetail(
                        new MemberPrincipal(2L, "CHILD"), "saving", 10L));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_COMPLETION_DETAIL_NOT_AVAILABLE,
                exception.getErrorCode());
    }

    @Test
    @DisplayName("부모 조회는 계약 조회 전에 자녀 접근 권한을 검증한다")
    void 부모_조회는_계약을_읽기_전에_자녀_접근권한을_검증한다() {
        MemberPrincipal parent = new MemberPrincipal(1L, "PARENT");

        assertThrows(BusinessException.class, () -> service.getChildCompletionDetail(
                new MemberPrincipal(2L, "CHILD"), 2L, "deposit", 10L));
        assertThrows(BusinessException.class, () -> service.getChildCompletionDetail(
                parent, 2L, "deposit", 10L));

        verify(familyAccessService).requireChildAccess(parent, 2L);
    }

    private FinancialProductEnrollmentVO enrollment(FinancialProductType type, String status) {
        FinancialProductEnrollmentVO enrollment = new FinancialProductEnrollmentVO();
        enrollment.setEnrollmentId(10L);
        enrollment.setProductType(type);
        enrollment.setProductName("상품");
        enrollment.setStatus(status);
        enrollment.setAppliedRate(new BigDecimal("4.50"));
        enrollment.setInterestCalculationType("SIMPLE");
        enrollment.setTermMonths(6);
        enrollment.setStartDate(LocalDate.of(2026, 1, 1));
        enrollment.setMaturityDate(LocalDate.of(2026, 7, 1));
        enrollment.setClosedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
        return enrollment;
    }

    private FinancialProductSettlementVO settlement(long principal, long interest) {
        FinancialProductSettlementVO settlement = new FinancialProductSettlementVO();
        settlement.setPrincipalAmount(principal);
        settlement.setInterestAmount(interest);
        return settlement;
    }

    private SavingPaymentHistoryVO savingHistory(
            int no, long paid, String status, String paidAt) {
        SavingPaymentHistoryVO history = new SavingPaymentHistoryVO();
        history.setInstallmentNo(no);
        history.setScheduledAmount(100_000L);
        history.setPaidAmount(paid);
        history.setStatus(status);
        history.setPaidAt(paidAt == null ? null : LocalDateTime.parse(paidAt));
        history.setCreatedAt(LocalDateTime.of(2026, no, 1, 0, 0));
        return history;
    }

    private LoanRepaymentHistoryVO loanHistory(
            int no, String type, long principal, long interest) {
        LoanRepaymentHistoryVO history = new LoanRepaymentHistoryVO();
        history.setInstallmentNo(no);
        history.setRepaymentType(type);
        history.setPrincipalAmount(principal);
        history.setPaidPrincipalAmount(principal);
        history.setInterestAmount(interest);
        history.setPaidInterestAmount(interest);
        history.setStatus("PAID");
        history.setPaidAt(LocalDateTime.of(2026, 2, 1, 0, 0));
        history.setCreatedAt(history.getPaidAt());
        return history;
    }
}
