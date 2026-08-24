package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductDetailResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductEnrollmentListResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductEnrollmentResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductListResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.ProductRateResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductBenefitVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductEnrollmentVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductSource;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingProductVO;
import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.teenyscore.exception.TeenyScoreErrorCode;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class FinancialProductService {

    private static final List<Integer> LOAN_TERMS = List.of(1, 3, 6, 12);
    private static final String INSUFFICIENT_GRADE = "INSUFFICIENT_GRADE";
    private static final String LOAN_GRADE_RESTRICTED =
            "LOAN_NOT_AVAILABLE_FOR_GRADE";
    private static final String PARENT_CANNOT_ENROLL =
            "PARENT_CANNOT_ENROLL";

    private final FinancialProductMapper financialProductMapper;
    private final FamilyAccessService familyAccessService;

    public FinancialProductService(
            FinancialProductMapper financialProductMapper,
            FamilyAccessService familyAccessService) {
        this.financialProductMapper = financialProductMapper;
        this.familyAccessService = familyAccessService;
    }

    @Transactional(readOnly = true)
    public List<FinancialProductListResponseDTO> getProducts(
            MemberPrincipal principal) {
        FinancialProductBenefitVO benefit = findBenefit(principal);
        List<FinancialProductListResponseDTO> products = new ArrayList<>();

        financialProductMapper.selectVisibleDepositProducts(principal.memberId()).stream()
                .map(product -> depositListItem(product, benefit))
                .forEach(products::add);
        financialProductMapper.selectVisibleSavingProducts(principal.memberId()).stream()
                .map(product -> savingListItem(product, benefit))
                .forEach(products::add);
        financialProductMapper.selectVisibleLoanProducts(principal.memberId()).stream()
                .map(product -> loanListItem(product, benefit))
                .forEach(products::add);
        return products;
    }

    @Transactional(readOnly = true)
    public List<FinancialProductListResponseDTO> getDepositProducts(
            MemberPrincipal principal) {
        FinancialProductBenefitVO benefit = findBenefit(principal);
        return financialProductMapper.selectVisibleDepositProducts(principal.memberId()).stream()
                .map(product -> depositListItem(product, benefit))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialProductListResponseDTO> getSavingProducts(
            MemberPrincipal principal) {
        FinancialProductBenefitVO benefit = findBenefit(principal);
        return financialProductMapper.selectVisibleSavingProducts(principal.memberId()).stream()
                .map(product -> savingListItem(product, benefit))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialProductListResponseDTO> getLoanProducts(
            MemberPrincipal principal) {
        FinancialProductBenefitVO benefit = findBenefit(principal);
        return financialProductMapper.selectVisibleLoanProducts(principal.memberId()).stream()
                .map(product -> loanListItem(product, benefit))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialProductEnrollmentListResponseDTO> getProductsByChildId(
            MemberPrincipal principal,
            Long childId) {
        requireParentChildAccess(principal, childId);
        List<FinancialProductEnrollmentListResponseDTO> enrollments =
                new ArrayList<>();
        financialProductMapper.selectDepositEnrollmentsByChildId(childId)
                .stream().map(FinancialProductEnrollmentListResponseDTO::of)
                .forEach(enrollments::add);
        financialProductMapper.selectSavingEnrollmentsByChildId(childId)
                .stream().map(FinancialProductEnrollmentListResponseDTO::of)
                .forEach(enrollments::add);
        financialProductMapper.selectLoanEnrollmentsByChildId(childId)
                .stream().map(FinancialProductEnrollmentListResponseDTO::of)
                .forEach(enrollments::add);
        return enrollments;
    }

    @Transactional(readOnly = true)
    public List<FinancialProductEnrollmentListResponseDTO>
            getDepositProductsByChildId(
            MemberPrincipal principal,
            Long childId) {
        requireParentChildAccess(principal, childId);
        return financialProductMapper.selectDepositEnrollmentsByChildId(childId)
                .stream()
                .map(FinancialProductEnrollmentListResponseDTO::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialProductEnrollmentListResponseDTO>
            getSavingProductsByChildId(
            MemberPrincipal principal,
            Long childId) {
        requireParentChildAccess(principal, childId);
        return financialProductMapper.selectSavingEnrollmentsByChildId(childId)
                .stream()
                .map(FinancialProductEnrollmentListResponseDTO::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialProductEnrollmentListResponseDTO>
            getLoanProductsByChildId(
            MemberPrincipal principal,
            Long childId) {
        requireParentChildAccess(principal, childId);
        return financialProductMapper.selectLoanEnrollmentsByChildId(childId)
                .stream()
                .map(FinancialProductEnrollmentListResponseDTO::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialProductEnrollmentListResponseDTO> getMyEnrollments(
            MemberPrincipal principal) {
        Long childId = requireChild(principal);
        List<FinancialProductEnrollmentListResponseDTO> enrollments =
                new ArrayList<>();
        financialProductMapper.selectDepositEnrollmentsByChildId(childId)
                .stream().map(FinancialProductEnrollmentListResponseDTO::of)
                .forEach(enrollments::add);
        financialProductMapper.selectSavingEnrollmentsByChildId(childId)
                .stream().map(FinancialProductEnrollmentListResponseDTO::of)
                .forEach(enrollments::add);
        financialProductMapper.selectLoanEnrollmentsByChildId(childId)
                .stream().map(FinancialProductEnrollmentListResponseDTO::of)
                .forEach(enrollments::add);
        return enrollments;
    }

    @Transactional(readOnly = true)
    public List<FinancialProductEnrollmentListResponseDTO>
            getMyDepositEnrollments(MemberPrincipal principal) {
        return financialProductMapper
                .selectDepositEnrollmentsByChildId(requireChild(principal))
                .stream()
                .map(FinancialProductEnrollmentListResponseDTO::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialProductEnrollmentListResponseDTO>
            getMySavingEnrollments(MemberPrincipal principal) {
        return financialProductMapper
                .selectSavingEnrollmentsByChildId(requireChild(principal))
                .stream()
                .map(FinancialProductEnrollmentListResponseDTO::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialProductEnrollmentListResponseDTO>
            getMyLoanEnrollments(MemberPrincipal principal) {
        return financialProductMapper
                .selectLoanEnrollmentsByChildId(requireChild(principal))
                .stream()
                .map(FinancialProductEnrollmentListResponseDTO::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public FinancialProductDetailResponseDTO getProductDetail(
            MemberPrincipal principal,
            String productType,
            Long productId) {
        FinancialProductBenefitVO benefit = findBenefit(principal);
        return switch (FinancialProductType.from(productType)) {
            case DEPOSIT -> depositDetail(
                    findDeposit(productId, principal.memberId()), benefit);
            case SAVING -> savingDetail(
                    findSaving(productId, principal.memberId()), benefit);
            case LOAN -> loanDetail(
                    findLoan(productId, principal.memberId()), benefit);
        };
    }

    @Transactional(readOnly = true)
    public FinancialProductDetailResponseDTO getDepositProductDetail(
            MemberPrincipal principal,
            Long productId) {
        return depositDetail(findDeposit(productId, principal.memberId()),
                findBenefit(principal));
    }

    @Transactional(readOnly = true)
    public FinancialProductDetailResponseDTO getSavingProductDetail(
            MemberPrincipal principal,
            Long productId) {
        return savingDetail(findSaving(productId, principal.memberId()),
                findBenefit(principal));
    }

    @Transactional(readOnly = true)
    public FinancialProductDetailResponseDTO getLoanProductDetail(
            MemberPrincipal principal,
            Long productId) {
        return loanDetail(findLoan(productId, principal.memberId()),
                findBenefit(principal));
    }

    @Transactional(readOnly = true)
    public FinancialProductEnrollmentResponseDTO getProductDetailByChildId(
            MemberPrincipal principal,
            Long childId,
            String productType,
            Long enrollmentId) {
        requireParentChildAccess(principal, childId);
        return enrollmentDetail(childId, productType, enrollmentId);
    }

    private FinancialProductEnrollmentResponseDTO enrollmentDetail(
            Long childId,
            String productType,
            Long enrollmentId) {
        FinancialProductEnrollmentVO enrollment = switch (
                FinancialProductType.from(productType)) {
            case DEPOSIT -> financialProductMapper
                    .selectDepositEnrollmentByChildIdAndId(
                            childId, enrollmentId);
            case SAVING -> financialProductMapper
                    .selectSavingEnrollmentByChildIdAndId(
                            childId, enrollmentId);
            case LOAN -> financialProductMapper
                    .selectLoanEnrollmentByChildIdAndId(
                            childId, enrollmentId);
        };
        if (enrollment == null) {
            throw new BusinessException(FinancialProductErrorCode
                    .FINANCIAL_PRODUCT_ENROLLMENT_NOT_FOUND);
        }
        return FinancialProductEnrollmentResponseDTO.of(enrollment);
    }

    private FinancialProductBenefitVO findBenefit(MemberPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_ROLE_FORBIDDEN);
        }
        if ("PARENT".equals(principal.role())) {
            FinancialProductBenefitVO parentView =
                    new FinancialProductBenefitVO();
            parentView.setBonusRate(BigDecimal.ZERO);
            return parentView;
        }
        if (!"CHILD".equals(principal.role())) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_ROLE_FORBIDDEN);
        }
        FinancialProductBenefitVO benefit =
                financialProductMapper.selectBenefitByChildId(
                        principal.memberId());
        if (benefit == null) {
            throw new BusinessException(
                    TeenyScoreErrorCode.TEENY_SCORE_CHILD_NOT_FOUND);
        }
        return benefit;
    }

    private void requireParentChildAccess(
            MemberPrincipal principal,
            Long childId) {
        if (principal == null || !"PARENT".equals(principal.role())) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_PARENT_ONLY);
        }
        familyAccessService.requireChildAccess(principal, childId);
    }

    private Long requireChild(MemberPrincipal principal) {
        if (principal == null || !"CHILD".equals(principal.role())) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_CHILD_ONLY);
        }
        return principal.memberId();
    }

    private DepositProductVO findDeposit(Long id, Long memberId) {
        DepositProductVO product =
                financialProductMapper.selectVisibleDepositProductById(id, memberId);
        if (product == null) {
            throw productNotFound();
        }
        return product;
    }

    private SavingProductVO findSaving(Long id, Long memberId) {
        SavingProductVO product =
                financialProductMapper.selectVisibleSavingProductById(id, memberId);
        if (product == null) {
            throw productNotFound();
        }
        return product;
    }

    private LoanProductVO findLoan(Long id, Long memberId) {
        LoanProductVO product =
                financialProductMapper.selectVisibleLoanProductById(id, memberId);
        if (product == null) {
            throw productNotFound();
        }
        return product;
    }

    private BusinessException productNotFound() {
        return new BusinessException(
                FinancialProductErrorCode.FINANCIAL_PRODUCT_NOT_FOUND);
    }

    /** 마이그레이션 이전 데이터의 null 출처는 기존 TEENY 상품으로 취급한다. */
    private String sourceName(FinancialProductSource source) {
        return source == null ? FinancialProductSource.TEENY.name() : source.name();
    }

    /** 부모의 커스텀 상품 조회(CustomFinancialProductService)에서도 같은 문구로 재사용한다. */
    FinancialProductListResponseDTO depositListItem(
            DepositProductVO product,
            FinancialProductBenefitVO benefit) {
        boolean eligible = gradeEligible(product.getRequiredGradeId(), benefit);
        List<ProductRateResponseDTO> rates = rates(
                product.getRate1m(), product.getRate3m(),
                product.getRate6m(), product.getRate12m(),
                benefit.getBonusRate());
        return FinancialProductListResponseDTO.builder()
                .productId(product.getId())
                .productType(FinancialProductType.DEPOSIT)
                .productSource(sourceName(product.getProductSource()))
                .financialCompanyName(product.getFinancialCompanyName())
                .productName(product.getName())
                .appliedGradeId(benefit.getGradeId())
                .appliedGradeName(benefit.getGradeName())
                .requiredGradeId(product.getRequiredGradeId())
                .requiredGradeName(product.getRequiredGradeName())
                .eligible(eligible)
                .ineligibleReason(ineligibleReason(eligible, benefit))
                .availableTerms(terms(rates))
                .rates(rates)
                .minimumAmount(product.getMinAmount())
                .maximumAmount(product.getMaxAmount())
                .interestCalculationType(
                        product.getInterestCalculationType())
                .build();
    }

    FinancialProductListResponseDTO savingListItem(
            SavingProductVO product,
            FinancialProductBenefitVO benefit) {
        boolean eligible = gradeEligible(product.getRequiredGradeId(), benefit);
        List<ProductRateResponseDTO> rates = rates(
                product.getRate1m(), product.getRate3m(),
                product.getRate6m(), product.getRate12m(),
                benefit.getBonusRate());
        return FinancialProductListResponseDTO.builder()
                .productId(product.getId())
                .productType(FinancialProductType.SAVING)
                .productSource(sourceName(product.getProductSource()))
                .financialCompanyName(product.getFinancialCompanyName())
                .productName(product.getName())
                .appliedGradeId(benefit.getGradeId())
                .appliedGradeName(benefit.getGradeName())
                .requiredGradeId(product.getRequiredGradeId())
                .requiredGradeName(product.getRequiredGradeName())
                .eligible(eligible)
                .ineligibleReason(ineligibleReason(eligible, benefit))
                .availableTerms(terms(rates))
                .rates(rates)
                .minimumAmount(product.getMinMonthAmount())
                .maximumAmount(product.getMaxMonthAmount())
                .savingsType(product.getSavingsType())
                .interestCalculationType(
                        product.getInterestCalculationType())
                .build();
    }

    FinancialProductListResponseDTO loanListItem(
            LoanProductVO product,
            FinancialProductBenefitVO benefit) {
        boolean eligible = loanEligible(product, benefit);
        return FinancialProductListResponseDTO.builder()
                .productId(product.getId())
                .productType(FinancialProductType.LOAN)
                .productSource(sourceName(product.getProductSource()))
                .productName(product.getName())
                .appliedGradeId(benefit.getGradeId())
                .appliedGradeName(benefit.getGradeName())
                .requiredGradeId(product.getRequiredGradeId())
                .requiredGradeName(product.getRequiredGradeName())
                .eligible(eligible)
                .ineligibleReason(loanIneligibleReason(product, benefit))
                .availableTerms(loanTerms(product))
                .baseRate(product.getBaseRate())
                .expectedAppliedRate(loanRate(product))
                .lateFeeRate(product.getLateFeeRate())
                .minimumAmount(product.getMinAmount())
                .maximumAmount(product.getMaxAmount())
                .repaymentType(product.getRepaymentType())
                .build();
    }

    private FinancialProductDetailResponseDTO depositDetail(
            DepositProductVO product,
            FinancialProductBenefitVO benefit) {
        boolean eligible = gradeEligible(product.getRequiredGradeId(), benefit);
        List<ProductRateResponseDTO> rates = rates(
                product.getRate1m(), product.getRate3m(),
                product.getRate6m(), product.getRate12m(),
                benefit.getBonusRate());
        return FinancialProductDetailResponseDTO.builder()
                .productId(product.getId())
                .productType(FinancialProductType.DEPOSIT)
                .productSource(sourceName(product.getProductSource()))
                .financialCompanyName(product.getFinancialCompanyName())
                .productName(product.getName())
                .description(product.getDescription())
                .appliedGradeId(benefit.getGradeId())
                .appliedGradeName(benefit.getGradeName())
                .requiredGradeId(product.getRequiredGradeId())
                .requiredGradeName(product.getRequiredGradeName())
                .eligible(eligible)
                .ineligibleReason(ineligibleReason(eligible, benefit))
                .availableTerms(terms(rates))
                .rates(rates)
                .earlyTerminationRate(product.getEarlyTerminationRate())
                .minimumAmount(product.getMinAmount())
                .maximumAmount(product.getMaxAmount())
                .interestCalculationType(
                        product.getInterestCalculationType())
                .build();
    }

    private FinancialProductDetailResponseDTO savingDetail(
            SavingProductVO product,
            FinancialProductBenefitVO benefit) {
        boolean eligible = gradeEligible(product.getRequiredGradeId(), benefit);
        List<ProductRateResponseDTO> rates = rates(
                product.getRate1m(), product.getRate3m(),
                product.getRate6m(), product.getRate12m(),
                benefit.getBonusRate());
        return FinancialProductDetailResponseDTO.builder()
                .productId(product.getId())
                .productType(FinancialProductType.SAVING)
                .productSource(sourceName(product.getProductSource()))
                .financialCompanyName(product.getFinancialCompanyName())
                .productName(product.getName())
                .description(product.getDescription())
                .appliedGradeId(benefit.getGradeId())
                .appliedGradeName(benefit.getGradeName())
                .requiredGradeId(product.getRequiredGradeId())
                .requiredGradeName(product.getRequiredGradeName())
                .eligible(eligible)
                .ineligibleReason(ineligibleReason(eligible, benefit))
                .availableTerms(terms(rates))
                .rates(rates)
                .earlyTerminationRate(product.getEarlyTerminationRate())
                .minimumAmount(product.getMinMonthAmount())
                .maximumAmount(product.getMaxMonthAmount())
                .savingsType(product.getSavingsType())
                .interestCalculationType(
                        product.getInterestCalculationType())
                .build();
    }

    private FinancialProductDetailResponseDTO loanDetail(
            LoanProductVO product,
            FinancialProductBenefitVO benefit) {
        return FinancialProductDetailResponseDTO.builder()
                .productId(product.getId())
                .productType(FinancialProductType.LOAN)
                .productSource(sourceName(product.getProductSource()))
                .productName(product.getName())
                .description(product.getDescription())
                .appliedGradeId(benefit.getGradeId())
                .appliedGradeName(benefit.getGradeName())
                .requiredGradeId(product.getRequiredGradeId())
                .requiredGradeName(product.getRequiredGradeName())
                .eligible(loanEligible(product, benefit))
                .ineligibleReason(loanIneligibleReason(product, benefit))
                .availableTerms(loanTerms(product))
                .baseRate(product.getBaseRate())
                .expectedAppliedRate(loanRate(product))
                .lateFeeRate(product.getLateFeeRate())
                .minimumAmount(product.getMinAmount())
                .maximumAmount(product.getMaxAmount())
                .repaymentType(product.getRepaymentType())
                .build();
    }

    private boolean loanEligible(
            LoanProductVO product,
            FinancialProductBenefitVO benefit) {
        return benefit.getLoanRate() != null
                && benefit.getGradeId() != null
                && product.getRequiredGradeId() != null
                && benefit.getGradeId() >= product.getRequiredGradeId();
    }

    private boolean gradeEligible(
            Long requiredGradeId, FinancialProductBenefitVO benefit) {
        return !isParentView(benefit)
                && (requiredGradeId == null
                || benefit.getGradeId() != null
                && benefit.getGradeId() >= requiredGradeId);
    }

    /** 부모 생성 상품을 포함한 대출의 예상금리는 상품에 설정된 기본금리를 사용한다. */
    private BigDecimal loanRate(LoanProductVO product) {
        // 목록·상세의 예상금리도 가입 시 저장될 계약 금리와 동일해야 한다.
        return product.getBaseRate();
    }

    /** 기간별 Boolean 컬럼을 API의 availableTerms 배열로 변환한다. */
    private List<Integer> loanTerms(LoanProductVO product) {
        List<Integer> terms = new ArrayList<>();
        if (Boolean.TRUE.equals(product.getAvailable1m())) terms.add(1);
        if (Boolean.TRUE.equals(product.getAvailable3m())) terms.add(3);
        if (Boolean.TRUE.equals(product.getAvailable6m())) terms.add(6);
        if (Boolean.TRUE.equals(product.getAvailable12m())) terms.add(12);
        return terms.isEmpty() && product.getProductSource() != FinancialProductSource.PARENT
                ? LOAN_TERMS : terms;
    }

    private String loanIneligibleReason(
            LoanProductVO product,
            FinancialProductBenefitVO benefit) {
        if (isParentView(benefit)) {
            return PARENT_CANNOT_ENROLL;
        }
        if (benefit.getLoanRate() == null) {
            return LOAN_GRADE_RESTRICTED;
        }
        return loanEligible(product, benefit) ? null : INSUFFICIENT_GRADE;
    }

    private String ineligibleReason(
            boolean eligible,
            FinancialProductBenefitVO benefit) {
        if (isParentView(benefit)) {
            return PARENT_CANNOT_ENROLL;
        }
        return eligible ? null : INSUFFICIENT_GRADE;
    }

    private boolean isParentView(FinancialProductBenefitVO benefit) {
        return benefit.getChildId() == null;
    }

    private List<ProductRateResponseDTO> rates(
            BigDecimal rate1m,
            BigDecimal rate3m,
            BigDecimal rate6m,
            BigDecimal rate12m,
            BigDecimal bonusRate) {
        List<ProductRateResponseDTO> rates = new ArrayList<>();
        addRate(rates, 1, rate1m, bonusRate);
        addRate(rates, 3, rate3m, bonusRate);
        addRate(rates, 6, rate6m, bonusRate);
        addRate(rates, 12, rate12m, bonusRate);
        return rates;
    }

    private void addRate(
            List<ProductRateResponseDTO> rates,
            int term,
            BigDecimal baseRate,
            BigDecimal bonusRate) {
        if (baseRate != null) {
            rates.add(new ProductRateResponseDTO(term, baseRate, bonusRate));
        }
    }

    private List<Integer> terms(List<ProductRateResponseDTO> rates) {
        return rates.stream()
                .map(ProductRateResponseDTO::getTermMonths)
                .toList();
    }
}
