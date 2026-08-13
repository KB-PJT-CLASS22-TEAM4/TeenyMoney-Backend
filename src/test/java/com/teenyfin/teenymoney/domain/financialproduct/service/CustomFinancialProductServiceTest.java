package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.CustomDepositProductRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.CustomLoanProductRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.CustomProductRateRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.CustomSavingProductRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.CustomFinancialProductResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductSource;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanProductVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomFinancialProductServiceTest {
    private static final MemberPrincipal PARENT =
            new MemberPrincipal(1L, "PARENT");
    private FinancialProductMapper mapper;
    private FamilyAccessService familyAccessService;
    private CustomFinancialProductService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        familyAccessService = mock(FamilyAccessService.class);
        service = new CustomFinancialProductService(mapper, familyAccessService);
    }

    @Test
    @DisplayName("부모는 예금의 1·3·6·12개월 금리를 각각 설정할 수 있다")
    void createParentDepositProductWithTermRates() {
        doAnswer(invocation -> {
            DepositProductVO product = invocation.getArgument(0);
            product.setId(15L);
            return 1;
        }).when(mapper).insertCustomDepositProduct(any());

        CustomFinancialProductResponseDTO response = service.createDeposit(
                PARENT, 2L, new CustomDepositProductRequestDTO(
                        "목표 예금", "목표 달성 예금", "SIMPLE",
                        allTermRates(), new BigDecimal("1.00"),
                        10_000L, 500_000L));

        ArgumentCaptor<DepositProductVO> captor =
                ArgumentCaptor.forClass(DepositProductVO.class);
        verify(familyAccessService).requireChildAccess(PARENT, 2L);
        verify(mapper).insertCustomDepositProduct(captor.capture());
        DepositProductVO product = captor.getValue();
        assertEquals(FinancialProductSource.PARENT, product.getProductSource());
        assertEquals(1L, product.getCreatedByParentId());
        assertEquals(2L, product.getTargetChildId());
        assertEquals(new BigDecimal("2.00"), product.getRate1m());
        assertEquals(new BigDecimal("2.50"), product.getRate3m());
        assertEquals(new BigDecimal("3.00"), product.getRate6m());
        assertEquals(new BigDecimal("4.00"), product.getRate12m());
        assertEquals(15L, response.getProductId());
        assertEquals("PARENT", response.getProductSource());
    }

    @Test
    @DisplayName("같은 가입기간의 금리를 중복 입력하면 생성을 차단한다")
    void duplicateTermRateIsRejected() {
        List<CustomProductRateRequestDTO> rates = List.of(
                new CustomProductRateRequestDTO(12, new BigDecimal("3.00")),
                new CustomProductRateRequestDTO(12, new BigDecimal("4.00")));

        assertThrows(BusinessException.class, () -> service.createSaving(
                PARENT, 2L, new CustomSavingProductRequestDTO(
                        "목표 적금", null, "FIXED", "SIMPLE", rates,
                        new BigDecimal("1.00"), 10_000L, 500_000L)));

        verify(mapper, never()).insertCustomSavingProduct(any());
    }

    @Test
    @DisplayName("중도해지 금리가 가장 낮은 기간 금리보다 높으면 생성을 차단한다")
    void earlyTerminationRateCannotExceedMinimumProductRate() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createSaving(PARENT, 2L,
                        new CustomSavingProductRequestDTO(
                                "목표 적금", null, "FIXED", "SIMPLE",
                                allTermRates(), new BigDecimal("2.10"),
                                10_000L, 500_000L)));

        assertEquals(FinancialProductErrorCode
                        .FINANCIAL_PRODUCT_CUSTOM_INVALID_CONDITION,
                exception.getErrorCode());
        verify(mapper, never()).insertCustomSavingProduct(any());
    }

    @Test
    @DisplayName("부모는 자녀 전용 대출의 가입기간을 지정할 수 있다")
    void createParentLoanWithFixedTerm() {
        when(mapper.countGradeById(2L)).thenReturn(1);
        doAnswer(invocation -> {
            LoanProductVO product = invocation.getArgument(0);
            product.setId(21L);
            return 1;
        }).when(mapper).insertCustomLoanProduct(any());

        CustomFinancialProductResponseDTO response = service.createLoan(
                PARENT, 2L, new CustomLoanProductRequestDTO(
                        "목표 대출", null, "BULLET", List.of(1, 3, 12),
                        new BigDecimal("5.00"), new BigDecimal("8.00"),
                        10_000L, 200_000L, 2L));

        ArgumentCaptor<LoanProductVO> captor =
                ArgumentCaptor.forClass(LoanProductVO.class);
        verify(mapper).insertCustomLoanProduct(captor.capture());
        assertEquals(true, captor.getValue().getAvailable1m());
        assertEquals(true, captor.getValue().getAvailable3m());
        assertEquals(false, captor.getValue().getAvailable6m());
        assertEquals(true, captor.getValue().getAvailable12m());
        assertEquals(21L, response.getProductId());
    }

    @Test
    @DisplayName("자녀 계정은 부모 전용 상품을 생성할 수 없다")
    void childCannotCreateCustomProduct() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createLoan(new MemberPrincipal(2L, "CHILD"), 2L,
                        new CustomLoanProductRequestDTO(
                                "목표 대출", null, "BULLET",
                                List.of(12), new BigDecimal("5.00"), new BigDecimal("8.00"),
                                10_000L, 200_000L, 2L)));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_PARENT_ONLY,
                exception.getErrorCode());
        verifyNoInteractions(familyAccessService);
        verify(mapper, never()).insertCustomLoanProduct(any());
    }

    private List<CustomProductRateRequestDTO> allTermRates() {
        return List.of(
                new CustomProductRateRequestDTO(1, new BigDecimal("2.00")),
                new CustomProductRateRequestDTO(3, new BigDecimal("2.50")),
                new CustomProductRateRequestDTO(6, new BigDecimal("3.00")),
                new CustomProductRateRequestDTO(12, new BigDecimal("4.00")));
    }
}
