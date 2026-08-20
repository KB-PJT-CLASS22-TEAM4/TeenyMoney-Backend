package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.CustomDepositProductRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.CustomLoanProductRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.CustomProductRateRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.CustomSavingProductRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.CustomFinancialProductResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductListResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductBenefitVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductSource;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingProductVO;
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
import static org.mockito.ArgumentMatchers.eq;
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
    private FinancialProductService financialProductService;
    private CustomFinancialProductService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        familyAccessService = mock(FamilyAccessService.class);
        financialProductService = mock(FinancialProductService.class);
        service = new CustomFinancialProductService(mapper, familyAccessService, financialProductService);
        FinancialProductBenefitVO benefit = new FinancialProductBenefitVO();
        benefit.setChildId(2L);
        when(mapper.selectBenefitByChildId(2L)).thenReturn(benefit);
    }

    @Test
    @DisplayName("부모 예금 생성 시 기간별 금리와 중도해지 기준금리를 상품에 저장한다")
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
        assertEquals(new BigDecimal("1.00"), product.getEarlyTerminationRate());
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

    @Test
    @DisplayName("본인이 만든 상품에 가입 중인 자녀가 없으면 삭제된다")
    void deleteDepositProductSucceedsWhenNoOpenEnrollments() {
        DepositProductVO product = new DepositProductVO();
        product.setId(15L);
        when(mapper.selectCustomDepositProductForDelete(1L, 2L, 15L)).thenReturn(product);
        when(mapper.countOpenDepositEnrollmentsByProductId(15L)).thenReturn(0);
        when(mapper.deactivateCustomDepositProduct(15L)).thenReturn(1);

        service.deleteDeposit(PARENT, 2L, 15L);

        verify(mapper).deactivateCustomDepositProduct(15L);
    }

    @Test
    @DisplayName("승인 대기 또는 가입 중인 자녀가 있으면 상품 삭제를 거절한다")
    void deleteDepositProductRejectsWhenOpenEnrollmentsExist() {
        DepositProductVO product = new DepositProductVO();
        product.setId(15L);
        when(mapper.selectCustomDepositProductForDelete(1L, 2L, 15L)).thenReturn(product);
        when(mapper.countOpenDepositEnrollmentsByProductId(15L)).thenReturn(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteDeposit(PARENT, 2L, 15L));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_CUSTOM_HAS_ENROLLMENTS,
                exception.getErrorCode());
        verify(mapper, never()).deactivateCustomDepositProduct(any());
    }

    @Test
    @DisplayName("본인이 만들지 않았거나 존재하지 않는 상품을 삭제하려 하면 404를 반환한다")
    void deleteDepositProductNotOwnedIsNotFound() {
        when(mapper.selectCustomDepositProductForDelete(1L, 2L, 15L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteDeposit(PARENT, 2L, 15L));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_NOT_FOUND,
                exception.getErrorCode());
        verify(mapper, never()).countOpenDepositEnrollmentsByProductId(any());
    }

    @Test
    @DisplayName("부모가 이 자녀에게 만든 예/적금/대출 커스텀 상품만 모아서, 자녀가 보는 형식으로 반환한다")
    void getCustomProductsReturnsOnlyThisParentsProductsForThisChild() {
        DepositProductVO deposit = new DepositProductVO();
        deposit.setId(15L);
        deposit.setName("목표 예금");
        SavingProductVO saving = new SavingProductVO();
        saving.setId(16L);
        saving.setName("목표 적금");
        when(mapper.selectCustomDepositProductsByParentAndChild(1L, 2L))
                .thenReturn(List.of(deposit));
        when(mapper.selectCustomSavingProductsByParentAndChild(1L, 2L))
                .thenReturn(List.of(saving));
        when(mapper.selectCustomLoanProductsByParentAndChild(1L, 2L))
                .thenReturn(List.of());
        // 실제 항목 조립(금리·가입가능 여부 등)은 FinancialProductService가 담당하므로,
        // 여기서는 자녀 혜택 기준으로 그 메서드에 위임했는지만 확인한다.
        when(financialProductService.depositListItem(eq(deposit), any())).thenReturn(
                FinancialProductListResponseDTO.builder()
                        .productId(15L).productType(FinancialProductType.DEPOSIT)
                        .productName("목표 예금").build());
        when(financialProductService.savingListItem(eq(saving), any())).thenReturn(
                FinancialProductListResponseDTO.builder()
                        .productId(16L).productType(FinancialProductType.SAVING)
                        .productName("목표 적금").build());

        List<FinancialProductListResponseDTO> products =
                service.getCustomProducts(PARENT, 2L);

        assertEquals(2, products.size());
        assertEquals(15L, products.get(0).getProductId());
        assertEquals("목표 예금", products.get(0).getProductName());
        assertEquals(16L, products.get(1).getProductId());
        verify(familyAccessService).requireChildAccess(PARENT, 2L);
    }

    @Test
    @DisplayName("자녀 계정으로 커스텀 상품 목록을 조회하면 거절한다")
    void getCustomProductsRejectsChildRole() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getCustomProducts(new MemberPrincipal(2L, "CHILD"), 2L));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_PARENT_ONLY,
                exception.getErrorCode());
        verifyNoInteractions(familyAccessService);
    }

    @Test
    @DisplayName("타입별 조회는 그 타입 상품만 반환한다")
    void getCustomDepositsReturnsOnlyDepositType() {
        DepositProductVO deposit = new DepositProductVO();
        deposit.setId(15L);
        deposit.setName("목표 예금");
        when(mapper.selectCustomDepositProductsByParentAndChild(1L, 2L))
                .thenReturn(List.of(deposit));
        when(financialProductService.depositListItem(eq(deposit), any())).thenReturn(
                FinancialProductListResponseDTO.builder()
                        .productId(15L).productType(FinancialProductType.DEPOSIT)
                        .productName("목표 예금").build());

        List<FinancialProductListResponseDTO> products =
                service.getCustomDeposits(PARENT, 2L);

        assertEquals(1, products.size());
        assertEquals(FinancialProductType.DEPOSIT, products.get(0).getProductType());
        verify(mapper, never()).selectCustomSavingProductsByParentAndChild(any(), any());
        verify(mapper, never()).selectCustomLoanProductsByParentAndChild(any(), any());
    }

    private List<CustomProductRateRequestDTO> allTermRates() {
        return List.of(
                new CustomProductRateRequestDTO(1, new BigDecimal("2.00")),
                new CustomProductRateRequestDTO(3, new BigDecimal("2.50")),
                new CustomProductRateRequestDTO(6, new BigDecimal("3.00")),
                new CustomProductRateRequestDTO(12, new BigDecimal("4.00")));
    }
}
