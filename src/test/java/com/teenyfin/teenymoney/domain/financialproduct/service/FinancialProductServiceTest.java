package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductDetailResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductListResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductBenefitVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanProductVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialProductServiceTest {

    private static final MemberPrincipal CHILD =
            new MemberPrincipal(2L, "CHILD");

    private FinancialProductMapper mapper;
    private FinancialProductService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        service = new FinancialProductService(mapper);
        when(mapper.selectBenefitByChildId(2L)).thenReturn(benefit());
        when(mapper.selectActiveDepositProducts()).thenReturn(List.of());
        when(mapper.selectActiveSavingProducts()).thenReturn(List.of());
        when(mapper.selectActiveLoanProducts()).thenReturn(List.of());
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
    }

    @Test
    void loanDetailReturnsGradeRateAndFixedTerms() {
        when(mapper.selectActiveLoanProductById(1L)).thenReturn(loan());

        FinancialProductDetailResponseDTO response =
                service.getProductDetail(CHILD, "loan", 1L);

        assertEquals(new BigDecimal("5.00"),
                response.getExpectedAppliedRate());
        assertEquals(List.of(1, 3, 6, 12), response.getAvailableTerms());
    }

    @Test
    void childCanUseLoanWithLowerMinimumScore() {
        LoanProductVO lowerGradeLoan = loan();
        lowerGradeLoan.setMinTeenyScore(450);
        when(mapper.selectActiveLoanProducts()).thenReturn(
                List.of(lowerGradeLoan));

        FinancialProductListResponseDTO response =
                service.getLoanProducts(CHILD).get(0);

        assertTrue(response.isEligible());
    }

    @Test
    void childCannotUseLoanWithHigherMinimumScore() {
        LoanProductVO higherGradeLoan = loan();
        higherGradeLoan.setMinTeenyScore(750);
        when(mapper.selectActiveLoanProducts()).thenReturn(
                List.of(higherGradeLoan));

        FinancialProductListResponseDTO response =
                service.getLoanProducts(CHILD).get(0);

        assertFalse(response.isEligible());
        assertEquals("INSUFFICIENT_TEENY_SCORE",
                response.getIneligibleReason());
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
        product.setMinTeenyScore(650);
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
        product.setMinTeenyScore(450);
        return product;
    }
}
