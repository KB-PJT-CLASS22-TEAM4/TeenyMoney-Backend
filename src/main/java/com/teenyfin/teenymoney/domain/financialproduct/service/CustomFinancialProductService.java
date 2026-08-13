package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.*;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.CustomFinancialProductResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.*;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 부모가 특정 자녀에게만 노출되는 금융상품을 생성한다. */
/**
 * 부모가 설정한 조건을 PARENT 상품으로 저장한다.
 * 상품 생성만 담당하며 가입계약 생성과 승인·거절은 기존 가입 흐름을 재사용한다.
 */
@Service
public class CustomFinancialProductService {
    private static final Set<Integer> TERMS = Set.of(1, 3, 6, 12);
    private static final Set<String> INTEREST_TYPES = Set.of("SIMPLE", "COMPOUND");
    private static final Set<String> SAVING_TYPES = Set.of("FIXED", "FREE");
    private static final Set<String> REPAYMENT_TYPES = Set.of(
            "EQUAL_PRINCIPAL_INTEREST", "EQUAL_PRINCIPAL", "BULLET");

    private final FinancialProductMapper financialProductMapper;
    private final FamilyAccessService familyAccessService;

    public CustomFinancialProductService(
            FinancialProductMapper financialProductMapper,
            FamilyAccessService familyAccessService) {
        this.financialProductMapper = financialProductMapper;
        this.familyAccessService = familyAccessService;
    }

    @Transactional
    public CustomFinancialProductResponseDTO createDeposit(
            MemberPrincipal principal, Long childId,
            CustomDepositProductRequestDTO request) {
        Long parentId = requireParentAndChildAccess(principal, childId);
        validateAmount(request.getMinimumAmount(), request.getMaximumAmount());
        validateInterestType(request.getInterestCalculationType());
        validateRates(request.getRates(), request.getEarlyTerminationRate());

        DepositProductVO product = new DepositProductVO();
        setScope(product, parentId, childId);
        product.setName(request.getProductName());
        product.setDescription(request.getDescription());
        product.setInterestCalculationType(request.getInterestCalculationType());
        applyDepositRates(product, request.getRates());
        product.setEarlyTerminationRate(request.getEarlyTerminationRate());
        product.setMinAmount(request.getMinimumAmount());
        product.setMaxAmount(request.getMaximumAmount());
        financialProductMapper.insertCustomDepositProduct(product);
        return response(product.getId(), FinancialProductType.DEPOSIT,
                product.getName(), childId);
    }

    @Transactional
    public CustomFinancialProductResponseDTO createSaving(
            MemberPrincipal principal, Long childId,
            CustomSavingProductRequestDTO request) {
        Long parentId = requireParentAndChildAccess(principal, childId);
        validateAmount(request.getMinimumMonthlyAmount(),
                request.getMaximumMonthlyAmount());
        validateInterestType(request.getInterestCalculationType());
        if (!SAVING_TYPES.contains(request.getSavingsType())) invalid();
        validateRates(request.getRates(), request.getEarlyTerminationRate());

        SavingProductVO product = new SavingProductVO();
        setScope(product, parentId, childId);
        product.setName(request.getProductName());
        product.setDescription(request.getDescription());
        product.setSavingsType(request.getSavingsType());
        product.setInterestCalculationType(request.getInterestCalculationType());
        applySavingRates(product, request.getRates());
        product.setEarlyTerminationRate(request.getEarlyTerminationRate());
        product.setMinMonthAmount(request.getMinimumMonthlyAmount());
        product.setMaxMonthAmount(request.getMaximumMonthlyAmount());
        financialProductMapper.insertCustomSavingProduct(product);
        return response(product.getId(), FinancialProductType.SAVING,
                product.getName(), childId);
    }

    @Transactional
    public CustomFinancialProductResponseDTO createLoan(
            MemberPrincipal principal, Long childId,
            CustomLoanProductRequestDTO request) {
        Long parentId = requireParentAndChildAccess(principal, childId);
        validateAmountAndRate(request.getMinimumAmount(),
                request.getMaximumAmount(), request.getInterestRate());
        if (!validLoanTerms(request.getAvailableTerms())
                || !REPAYMENT_TYPES.contains(request.getRepaymentType())
                || request.getLateFeeRate().compareTo(request.getInterestRate()) < 0
                || financialProductMapper.countGradeById(request.getRequiredGradeId()) == 0) {
            invalid();
        }

        LoanProductVO product = new LoanProductVO();
        setScope(product, parentId, childId);
        product.setName(request.getProductName());
        product.setDescription(request.getDescription());
        product.setRepaymentType(request.getRepaymentType());
        product.setBaseRate(request.getInterestRate());
        applyLoanTerms(product, request.getAvailableTerms());
        product.setLateFeeRate(request.getLateFeeRate());
        product.setMinAmount(request.getMinimumAmount());
        product.setMaxAmount(request.getMaximumAmount());
        product.setRequiredGradeId(request.getRequiredGradeId());
        financialProductMapper.insertCustomLoanProduct(product);
        return response(product.getId(), FinancialProductType.LOAN,
                product.getName(), childId);
    }

    /** 다른 가족의 자녀를 대상으로 부모 상품을 생성하지 못하도록 관계를 검증한다. */
    private Long requireParentAndChildAccess(MemberPrincipal principal, Long childId) {
        if (principal == null || !"PARENT".equals(principal.role())) {
            throw new BusinessException(FinancialProductErrorCode.FINANCIAL_PRODUCT_PARENT_ONLY);
        }
        familyAccessService.requireChildAccess(principal, childId);
        return principal.memberId();
    }

    private void validateAmountAndRate(Long minimum, Long maximum,
                                       BigDecimal rate) {
        validateAmount(minimum, maximum);
        if (rate.compareTo(BigDecimal.ZERO) <= 0) invalid();
    }

    private void validateAmount(Long minimum, Long maximum) {
        if (minimum > maximum) invalid();
    }

    private void validateInterestType(String type) {
        if (!INTEREST_TYPES.contains(type)) invalid();
    }

    /** 기간 중복을 막고 1·3·6·12개월 금리만 허용한다. */
    /**
     * 예·적금은 1·3·6·12개월 중 선택한 기간별 기본금리를 저장한다.
     * 중도해지 금리는 선택된 기간의 가장 낮은 기본금리를 넘을 수 없다.
     */
    private void validateRates(List<CustomProductRateRequestDTO> rates,
                               BigDecimal earlyRate) {
        if (rates == null || rates.isEmpty() || rates.size() > TERMS.size()
                || earlyRate == null || earlyRate.compareTo(BigDecimal.ZERO) <= 0) {
            invalid();
        }
        Set<Integer> requestedTerms = new HashSet<>();
        BigDecimal minimumRate = null;
        for (CustomProductRateRequestDTO rate : rates) {
            if (rate == null || !TERMS.contains(rate.getTermMonths())
                    || rate.getInterestRate() == null
                    || rate.getInterestRate().compareTo(BigDecimal.ZERO) <= 0
                    || !requestedTerms.add(rate.getTermMonths())) {
                invalid();
            }
            if (minimumRate == null
                    || rate.getInterestRate().compareTo(minimumRate) < 0) {
                minimumRate = rate.getInterestRate();
            }
        }
        if (earlyRate.compareTo(minimumRate) > 0) invalid();
    }

    private boolean validLoanTerms(List<Integer> terms) {
        return terms != null && !terms.isEmpty()
                && terms.size() <= TERMS.size()
                && terms.stream().allMatch(TERMS::contains)
                && new HashSet<>(terms).size() == terms.size();
    }

    /** 요청 배열을 조회와 가입 검증에 사용하는 기간별 가능 여부 컬럼으로 변환한다. */
    private void applyLoanTerms(LoanProductVO product, List<Integer> terms) {
        product.setAvailable1m(terms.contains(1));
        product.setAvailable3m(terms.contains(3));
        product.setAvailable6m(terms.contains(6));
        product.setAvailable12m(terms.contains(12));
    }

    private void invalid() {
        throw new BusinessException(
                FinancialProductErrorCode.FINANCIAL_PRODUCT_CUSTOM_INVALID_CONDITION);
    }

    /** PARENT 상품의 생성자와 공개 대상을 함께 저장해 조회 범위를 제한한다. */
    private void setScope(DepositProductVO product, Long parentId, Long childId) {
        product.setProductSource(FinancialProductSource.PARENT);
        product.setCreatedByParentId(parentId);
        product.setTargetChildId(childId);
    }

    private void setScope(SavingProductVO product, Long parentId, Long childId) {
        product.setProductSource(FinancialProductSource.PARENT);
        product.setCreatedByParentId(parentId);
        product.setTargetChildId(childId);
    }

    private void setScope(LoanProductVO product, Long parentId, Long childId) {
        product.setProductSource(FinancialProductSource.PARENT);
        product.setCreatedByParentId(parentId);
        product.setTargetChildId(childId);
    }

    /** 요청받은 예금 금리를 기존 기간별 금리 컬럼에 저장한다. */
    private void applyDepositRates(DepositProductVO product,
                                   List<CustomProductRateRequestDTO> rates) {
        rates.forEach(rate -> setTermRate(product, rate.getTermMonths(),
                rate.getInterestRate()));
    }

    /** 요청받은 적금 금리를 기존 기간별 금리 컬럼에 저장한다. */
    private void applySavingRates(SavingProductVO product,
                                  List<CustomProductRateRequestDTO> rates) {
        rates.forEach(rate -> setTermRate(product, rate.getTermMonths(),
                rate.getInterestRate()));
    }

    private void setTermRate(DepositProductVO product, int term, BigDecimal rate) {
        if (term == 1) product.setRate1m(rate);
        if (term == 3) product.setRate3m(rate);
        if (term == 6) product.setRate6m(rate);
        if (term == 12) product.setRate12m(rate);
    }

    private void setTermRate(SavingProductVO product, int term, BigDecimal rate) {
        if (term == 1) product.setRate1m(rate);
        if (term == 3) product.setRate3m(rate);
        if (term == 6) product.setRate6m(rate);
        if (term == 12) product.setRate12m(rate);
    }

    private CustomFinancialProductResponseDTO response(
            Long id, FinancialProductType type, String name, Long childId) {
        return CustomFinancialProductResponseDTO.builder()
                .productId(id).productType(type).productSource("PARENT")
                .productName(name).targetChildId(childId).build();
    }
}
