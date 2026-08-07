package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductDetailResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductListResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.ProductRateResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductBenefitVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductType;
import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingProductVO;
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
    private static final String INSUFFICIENT_SCORE = "INSUFFICIENT_TEENY_SCORE";
    private static final String LOAN_GRADE_RESTRICTED =
            "LOAN_NOT_AVAILABLE_FOR_GRADE";
    private static final String PARENT_CANNOT_ENROLL =
            "PARENT_CANNOT_ENROLL";

    private final FinancialProductMapper financialProductMapper;

    public FinancialProductService(FinancialProductMapper financialProductMapper) {
        this.financialProductMapper = financialProductMapper;
    }

    @Transactional(readOnly = true)
    public List<FinancialProductListResponseDTO> getProducts(
            MemberPrincipal principal) {
        FinancialProductBenefitVO benefit = findBenefit(principal);
        List<FinancialProductListResponseDTO> products = new ArrayList<>();

        financialProductMapper.selectActiveDepositProducts().stream()
                .map(product -> depositListItem(product, benefit))
                .forEach(products::add);
        financialProductMapper.selectActiveSavingProducts().stream()
                .map(product -> savingListItem(product, benefit))
                .forEach(products::add);
        financialProductMapper.selectActiveLoanProducts().stream()
                .map(product -> loanListItem(product, benefit))
                .forEach(products::add);
        return products;
    }

    @Transactional(readOnly = true)
    public List<FinancialProductListResponseDTO> getDepositProducts(
            MemberPrincipal principal) {
        FinancialProductBenefitVO benefit = findBenefit(principal);
        return financialProductMapper.selectActiveDepositProducts().stream()
                .map(product -> depositListItem(product, benefit))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialProductListResponseDTO> getSavingProducts(
            MemberPrincipal principal) {
        FinancialProductBenefitVO benefit = findBenefit(principal);
        return financialProductMapper.selectActiveSavingProducts().stream()
                .map(product -> savingListItem(product, benefit))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialProductListResponseDTO> getLoanProducts(
            MemberPrincipal principal) {
        FinancialProductBenefitVO benefit = findBenefit(principal);
        return financialProductMapper.selectActiveLoanProducts().stream()
                .map(product -> loanListItem(product, benefit))
                .toList();
    }

    @Transactional(readOnly = true)
    public FinancialProductDetailResponseDTO getProductDetail(
            MemberPrincipal principal,
            String productType,
            Long productId) {
        FinancialProductBenefitVO benefit = findBenefit(principal);
        return switch (FinancialProductType.from(productType)) {
            case DEPOSIT -> depositDetail(findDeposit(productId), benefit);
            case SAVING -> savingDetail(findSaving(productId), benefit);
            case LOAN -> loanDetail(findLoan(productId), benefit);
        };
    }

    @Transactional(readOnly = true)
    public FinancialProductDetailResponseDTO getDepositProductDetail(
            MemberPrincipal principal,
            Long productId) {
        return depositDetail(findDeposit(productId), findBenefit(principal));
    }

    @Transactional(readOnly = true)
    public FinancialProductDetailResponseDTO getSavingProductDetail(
            MemberPrincipal principal,
            Long productId) {
        return savingDetail(findSaving(productId), findBenefit(principal));
    }

    @Transactional(readOnly = true)
    public FinancialProductDetailResponseDTO getLoanProductDetail(
            MemberPrincipal principal,
            Long productId) {
        return loanDetail(findLoan(productId), findBenefit(principal));
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

    private DepositProductVO findDeposit(Long id) {
        DepositProductVO product =
                financialProductMapper.selectActiveDepositProductById(id);
        if (product == null) {
            throw productNotFound();
        }
        return product;
    }

    private SavingProductVO findSaving(Long id) {
        SavingProductVO product =
                financialProductMapper.selectActiveSavingProductById(id);
        if (product == null) {
            throw productNotFound();
        }
        return product;
    }

    private LoanProductVO findLoan(Long id) {
        LoanProductVO product =
                financialProductMapper.selectActiveLoanProductById(id);
        if (product == null) {
            throw productNotFound();
        }
        return product;
    }

    private BusinessException productNotFound() {
        return new BusinessException(
                FinancialProductErrorCode.FINANCIAL_PRODUCT_NOT_FOUND);
    }

    private FinancialProductListResponseDTO depositListItem(
            DepositProductVO product,
            FinancialProductBenefitVO benefit) {
        boolean eligible = scoreEligible(
                benefit.getTeenyScore(), product.getMinTeenyScore());
        List<ProductRateResponseDTO> rates = rates(
                product.getRate1m(), product.getRate3m(),
                product.getRate6m(), product.getRate12m(),
                benefit.getBonusRate());
        return FinancialProductListResponseDTO.builder()
                .productId(product.getId())
                .productType(FinancialProductType.DEPOSIT)
                .financialCompanyName(product.getFinancialCompanyName())
                .productName(product.getName())
                .minimumTeenyScore(product.getMinTeenyScore())
                .currentTeenyScore(benefit.getTeenyScore())
                .eligible(eligible)
                .ineligibleReason(ineligibleReason(eligible, benefit))
                .availableTerms(terms(rates))
                .rates(rates)
                .build();
    }

    private FinancialProductListResponseDTO savingListItem(
            SavingProductVO product,
            FinancialProductBenefitVO benefit) {
        boolean eligible = scoreEligible(
                benefit.getTeenyScore(), product.getMinTeenyScore());
        List<ProductRateResponseDTO> rates = rates(
                product.getRate1m(), product.getRate3m(),
                product.getRate6m(), product.getRate12m(),
                benefit.getBonusRate());
        return FinancialProductListResponseDTO.builder()
                .productId(product.getId())
                .productType(FinancialProductType.SAVING)
                .financialCompanyName(product.getFinancialCompanyName())
                .productName(product.getName())
                .minimumTeenyScore(product.getMinTeenyScore())
                .currentTeenyScore(benefit.getTeenyScore())
                .eligible(eligible)
                .ineligibleReason(ineligibleReason(eligible, benefit))
                .availableTerms(terms(rates))
                .rates(rates)
                .build();
    }

    private FinancialProductListResponseDTO loanListItem(
            LoanProductVO product,
            FinancialProductBenefitVO benefit) {
        boolean eligible = loanEligible(product, benefit);
        return FinancialProductListResponseDTO.builder()
                .productId(product.getId())
                .productType(FinancialProductType.LOAN)
                .productName(product.getName())
                .minimumTeenyScore(product.getMinTeenyScore())
                .currentTeenyScore(benefit.getTeenyScore())
                .eligible(eligible)
                .ineligibleReason(loanIneligibleReason(product, benefit))
                .availableTerms(LOAN_TERMS)
                .rates(List.of())
                .expectedAppliedRate(benefit.getLoanRate())
                .build();
    }

    private FinancialProductDetailResponseDTO depositDetail(
            DepositProductVO product,
            FinancialProductBenefitVO benefit) {
        boolean eligible = scoreEligible(
                benefit.getTeenyScore(), product.getMinTeenyScore());
        List<ProductRateResponseDTO> rates = rates(
                product.getRate1m(), product.getRate3m(),
                product.getRate6m(), product.getRate12m(),
                benefit.getBonusRate());
        return FinancialProductDetailResponseDTO.builder()
                .productId(product.getId())
                .productType(FinancialProductType.DEPOSIT)
                .financialCompanyName(product.getFinancialCompanyName())
                .productName(product.getName())
                .description(product.getDescription())
                .currentTeenyScore(benefit.getTeenyScore())
                .gradeName(benefit.getGradeName())
                .minimumTeenyScore(product.getMinTeenyScore())
                .eligible(eligible)
                .ineligibleReason(ineligibleReason(eligible, benefit))
                .availableTerms(terms(rates))
                .rates(rates)
                .earlyTerminationRate(product.getEarlyTerminationRate())
                .minimumAmount(product.getMinAmount())
                .maximumAmount(product.getMaxAmount())
                .build();
    }

    private FinancialProductDetailResponseDTO savingDetail(
            SavingProductVO product,
            FinancialProductBenefitVO benefit) {
        boolean eligible = scoreEligible(
                benefit.getTeenyScore(), product.getMinTeenyScore());
        List<ProductRateResponseDTO> rates = rates(
                product.getRate1m(), product.getRate3m(),
                product.getRate6m(), product.getRate12m(),
                benefit.getBonusRate());
        return FinancialProductDetailResponseDTO.builder()
                .productId(product.getId())
                .productType(FinancialProductType.SAVING)
                .financialCompanyName(product.getFinancialCompanyName())
                .productName(product.getName())
                .description(product.getDescription())
                .currentTeenyScore(benefit.getTeenyScore())
                .gradeName(benefit.getGradeName())
                .minimumTeenyScore(product.getMinTeenyScore())
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
                .productName(product.getName())
                .description(product.getDescription())
                .currentTeenyScore(benefit.getTeenyScore())
                .gradeName(benefit.getGradeName())
                .minimumTeenyScore(product.getMinTeenyScore())
                .eligible(loanEligible(product, benefit))
                .ineligibleReason(loanIneligibleReason(product, benefit))
                .availableTerms(LOAN_TERMS)
                .rates(List.of())
                .expectedAppliedRate(benefit.getLoanRate())
                .lateFeeRate(product.getLateFeeRate())
                .minimumAmount(product.getMinAmount())
                .maximumAmount(product.getMaxAmount())
                .repaymentType(product.getRepaymentType())
                .build();
    }

    private boolean scoreEligible(Integer score, int minimumScore) {
        return score != null && score >= minimumScore;
    }

    private boolean loanEligible(
            LoanProductVO product,
            FinancialProductBenefitVO benefit) {
        return benefit.getLoanRate() != null
                && scoreEligible(
                        benefit.getTeenyScore(), product.getMinTeenyScore());
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
        return scoreEligible(
                benefit.getTeenyScore(), product.getMinTeenyScore())
                ? null : INSUFFICIENT_SCORE;
    }

    private String ineligibleReason(
            boolean eligible,
            FinancialProductBenefitVO benefit) {
        if (isParentView(benefit)) {
            return PARENT_CANNOT_ENROLL;
        }
        return eligible ? null : INSUFFICIENT_SCORE;
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
