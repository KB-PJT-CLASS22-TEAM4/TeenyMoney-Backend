package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductDetailResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductEnrollmentResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductListResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductBenefitVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductEnrollmentVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanProductVO;
import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialProductServiceTest {

    private static final MemberPrincipal CHILD =
            new MemberPrincipal(2L, "CHILD");

    private FinancialProductMapper mapper;
    private FamilyAccessService familyAccessService;
    private FinancialProductService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        familyAccessService = mock(FamilyAccessService.class);
        service = new FinancialProductService(mapper, familyAccessService);
        when(mapper.selectBenefitByChildId(2L)).thenReturn(benefit());
        when(mapper.selectActiveDepositProducts()).thenReturn(List.of());
        when(mapper.selectActiveSavingProducts()).thenReturn(List.of());
        when(mapper.selectActiveLoanProducts()).thenReturn(List.of());
        when(mapper.selectDepositEnrollmentsByChildId(2L))
                .thenReturn(List.of());
        when(mapper.selectSavingEnrollmentsByChildId(2L))
                .thenReturn(List.of());
        when(mapper.selectLoanEnrollmentsByChildId(2L))
                .thenReturn(List.of());
    }

    @Test
    void depositRateAddsGradeBonus() {
        DepositProductVO deposit = deposit();
        when(mapper.selectActiveDepositProducts()).thenReturn(List.of(deposit));

        List<FinancialProductListResponseDTO> response =
                service.getProducts(CHILD);

        assertEquals(1, response.size());
        assertEquals(FinancialProductType.DEPOSIT,
                response.get(0).getProductType());
        assertTrue(response.get(0).isEligible());
        assertEquals(new BigDecimal("5.50"),
                response.get(0).getRates().get(0).getExpectedAppliedRate());
    }

    @Test
    void lowestGradeCannotUseLoan() {
        FinancialProductBenefitVO lowest = benefit();
        lowest.setLoanRate(null);
        when(mapper.selectBenefitByChildId(2L)).thenReturn(lowest);
        when(mapper.selectActiveLoanProducts()).thenReturn(List.of(loan()));

        FinancialProductListResponseDTO response =
                service.getProducts(CHILD).get(0);

        assertFalse(response.isEligible());
        assertEquals("LOAN_NOT_AVAILABLE_FOR_GRADE",
                response.getIneligibleReason());
        assertEquals(new BigDecimal("5.00"),
                response.getExpectedAppliedRate());
    }

    @Test
    void loanDetailReturnsProductRatesAndFixedTerms() {
        LoanProductVO loan = loan();
        loan.setBaseRate(new BigDecimal("3.50"));
        when(mapper.selectActiveLoanProductById(1L)).thenReturn(loan);

        FinancialProductDetailResponseDTO response =
                service.getProductDetail(CHILD, "loan", 1L);

        assertEquals(new BigDecimal("3.50"), response.getBaseRate());
        assertEquals(new BigDecimal("3.50"),
                response.getExpectedAppliedRate());
        assertEquals(new BigDecimal("8.00"), response.getLateFeeRate());
        assertEquals(List.of(1, 3, 6, 12), response.getAvailableTerms());
    }

    @Test
    void childCanUseLoanWhenAppliedGradeMeetsRequiredGrade() {
        LoanProductVO lowerGradeLoan = loan();
        lowerGradeLoan.setRequiredGradeId(2L);
        when(mapper.selectActiveLoanProducts()).thenReturn(
                List.of(lowerGradeLoan));

        FinancialProductListResponseDTO response =
                service.getLoanProducts(CHILD).get(0);

        assertTrue(response.isEligible());
    }

    @Test
    void childCannotUseLoanWhenAppliedGradeIsBelowRequiredGrade() {
        LoanProductVO higherGradeLoan = loan();
        higherGradeLoan.setRequiredGradeId(4L);
        when(mapper.selectActiveLoanProducts()).thenReturn(
                List.of(higherGradeLoan));

        FinancialProductListResponseDTO response =
                service.getLoanProducts(CHILD).get(0);

        assertFalse(response.isEligible());
        assertEquals("INSUFFICIENT_GRADE",
                response.getIneligibleReason());
    }

    @Test
    @DisplayName("실시간 점수가 높아도 월간 적용 등급이 요구등급보다 낮으면 대출 가입이 불가능하다")
    void realtimeScoreDoesNotChangeLoanEligibilityBeforeGradeUpdate() {
        FinancialProductBenefitVO starterBenefit = benefit();
        starterBenefit.setTeenyScore(660);
        starterBenefit.setGradeId(2L);
        starterBenefit.setGradeName("스타터");

        LoanProductVO plusLoan = loan();
        plusLoan.setRequiredGradeId(3L);
        plusLoan.setRequiredGradeName("플러스");

        when(mapper.selectBenefitByChildId(2L))
                .thenReturn(starterBenefit);
        when(mapper.selectActiveLoanProducts()).thenReturn(List.of(plusLoan));

        FinancialProductListResponseDTO response =
                service.getLoanProducts(CHILD).get(0);

        assertFalse(response.isEligible());
        assertEquals("INSUFFICIENT_GRADE",
                response.getIneligibleReason());
        assertEquals(2L, response.getAppliedGradeId());
        assertEquals(3L, response.getRequiredGradeId());
    }

    @Test
    @DisplayName("부모는 다른 부모에게 연결된 자녀의 금융상품을 조회할 수 없다")
    void parentCannotReadAnotherParentsChildProducts() {
        MemberPrincipal otherParent = new MemberPrincipal(9L, "PARENT");
        doThrow(new BusinessException(CommonErrorCode.AUTH_FORBIDDEN))
                .when(familyAccessService)
                .requireChildAccess(otherParent, 2L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getDepositProductsByChildId(
                        otherParent, 2L));

        assertEquals(CommonErrorCode.AUTH_FORBIDDEN,
                exception.getErrorCode());
        verify(mapper, never()).selectDepositEnrollmentsByChildId(2L);
    }

    @Test
    @DisplayName("부모가 존재하지 않는 자녀 ID를 조회하면 금융상품 데이터에 접근하지 않고 거부한다")
    void parentCannotReadNonexistentChildProducts() {
        MemberPrincipal parent = new MemberPrincipal(1L, "PARENT");
        Long nonexistentChildId = 999L;
        doThrow(new BusinessException(CommonErrorCode.AUTH_FORBIDDEN))
                .when(familyAccessService)
                .requireChildAccess(parent, nonexistentChildId);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getProductsByChildId(
                        parent, nonexistentChildId));

        assertEquals(CommonErrorCode.AUTH_FORBIDDEN,
                exception.getErrorCode());
        verify(mapper, never())
                .selectDepositEnrollmentsByChildId(nonexistentChildId);
        verify(mapper, never())
                .selectSavingEnrollmentsByChildId(nonexistentChildId);
        verify(mapper, never())
                .selectLoanEnrollmentsByChildId(nonexistentChildId);
    }

    @Test
    @DisplayName("부모는 자녀의 현재 등급이 아니라 가입 원장에 저장된 계약금리와 금액을 조회한다")
    void parentReadsRequestedChildEnrollmentContract() {
        MemberPrincipal parent = new MemberPrincipal(1L, "PARENT");
        when(mapper.selectDepositEnrollmentsByChildId(2L)).thenReturn(
                List.of(depositEnrollment()));

        FinancialProductEnrollmentResponseDTO response =
                service.getProductsByChildId(parent, 2L).get(0);

        verify(familyAccessService).requireChildAccess(parent, 2L);
        verify(mapper).selectDepositEnrollmentsByChildId(2L);
        verify(mapper, never()).selectActiveDepositProducts();
        verify(mapper, never()).selectBenefitByChildId(2L);
        assertEquals(11L, response.getEnrollmentId());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals(new BigDecimal("4.50"), response.getAppliedRate());
        assertEquals(100_000L, response.getDepositAmount());
    }

    @Test
    void parentCannotReadDetailOfProductChildDidNotEnroll() {
        MemberPrincipal parent = new MemberPrincipal(1L, "PARENT");
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getProductDetailByChildId(
                        parent, 2L, "deposit", 99L));

        verify(mapper).selectDepositEnrollmentByChildIdAndId(2L, 99L);
        assertEquals(FinancialProductErrorCode
                        .FINANCIAL_PRODUCT_ENROLLMENT_NOT_FOUND,
                exception.getErrorCode());
    }

    @Test
    @DisplayName("같은 상품에 여러 번 가입한 경우 각 계약을 enrollmentId로 구분하여 반환한다")
    void duplicateProductEnrollmentsRemainSeparateContracts() {
        MemberPrincipal parent = new MemberPrincipal(1L, "PARENT");
        FinancialProductEnrollmentVO first = depositEnrollment();
        FinancialProductEnrollmentVO second = depositEnrollment();
        second.setEnrollmentId(12L);
        when(mapper.selectDepositEnrollmentsByChildId(2L))
                .thenReturn(List.of(first, second));

        List<FinancialProductEnrollmentResponseDTO> response =
                service.getDepositProductsByChildId(parent, 2L);

        assertEquals(2, response.size());
        assertEquals(11L, response.get(0).getEnrollmentId());
        assertEquals(12L, response.get(1).getEnrollmentId());
        assertEquals(response.get(0).getProductId(),
                response.get(1).getProductId());
    }

    @Test
    void childCannotUseParentChildProductEndpoint() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getProductsByChildId(CHILD, 2L));

        assertEquals(
                FinancialProductErrorCode.FINANCIAL_PRODUCT_PARENT_ONLY,
                exception.getErrorCode());
    }

    @Test
    void missingProductThrowsNotFound() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getProductDetail(CHILD, "deposit", 999L));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_NOT_FOUND,
                exception.getErrorCode());
    }

    @Test
    void parentCanReadProductsButCannotEnroll() {
        when(mapper.selectActiveDepositProducts()).thenReturn(
                List.of(deposit()));

        FinancialProductListResponseDTO response = service.getProducts(
                new MemberPrincipal(1L, "PARENT")).get(0);

        assertFalse(response.isEligible());
        assertEquals("PARENT_CANNOT_ENROLL",
                response.getIneligibleReason());
        assertEquals(new BigDecimal("3.50"),
                response.getRates().get(0).getExpectedAppliedRate());
    }

    private FinancialProductBenefitVO benefit() {
        FinancialProductBenefitVO benefit = new FinancialProductBenefitVO();
        benefit.setChildId(2L);
        benefit.setTeenyScore(700);
        benefit.setGradeId(3L);
        benefit.setGradeName("플러스");
        benefit.setBonusRate(new BigDecimal("2.00"));
        benefit.setLoanRate(new BigDecimal("5.00"));
        return benefit;
    }

    private DepositProductVO deposit() {
        DepositProductVO product = new DepositProductVO();
        product.setId(1L);
        product.setName("정기예금");
        product.setFinancialCompanyName("국민은행");
        product.setRate12m(new BigDecimal("3.50"));
        return product;
    }

    private LoanProductVO loan() {
        LoanProductVO product = new LoanProductVO();
        product.setId(1L);
        product.setName("티니 대출");
        product.setBaseRate(new BigDecimal("5.00"));
        product.setLateFeeRate(new BigDecimal("8.00"));
        product.setRepaymentType("EQUAL_PRINCIPAL_INTEREST");
        product.setMinAmount(10_000L);
        product.setMaxAmount(200_000L);
        product.setRequiredGradeId(2L);
        product.setRequiredGradeName("스타터");
        return product;
    }

    private FinancialProductEnrollmentVO depositEnrollment() {
        FinancialProductEnrollmentVO enrollment =
                new FinancialProductEnrollmentVO();
        enrollment.setEnrollmentId(11L);
        enrollment.setProductId(1L);
        enrollment.setProductType(FinancialProductType.DEPOSIT);
        enrollment.setProductName("정기예금");
        enrollment.setStatus("ACTIVE");
        enrollment.setAppliedRate(new BigDecimal("4.50"));
        enrollment.setTermMonths(12);
        enrollment.setStartDate(LocalDate.of(2026, 8, 1));
        enrollment.setMaturityDate(LocalDate.of(2027, 8, 1));
        enrollment.setDepositAmount(100_000L);
        return enrollment;
    }
}
