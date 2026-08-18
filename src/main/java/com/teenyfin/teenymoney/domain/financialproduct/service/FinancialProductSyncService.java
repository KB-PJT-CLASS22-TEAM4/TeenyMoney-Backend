package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.finlife.FinlifeClient;
import com.teenyfin.teenymoney.domain.financialproduct.finlife.dto.FinlifeApiResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.finlife.dto.FinlifeProductBaseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.finlife.dto.FinlifeProductOptionDTO;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingProductVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FinancialProductSyncService {

    private static final Set<Integer> SUPPORTED_TERMS = Set.of(1, 3, 6, 12);
    private static final BigDecimal DEFAULT_DEPOSIT_TERMINATION_RATE =
            new BigDecimal("0.50");
    private static final BigDecimal DEFAULT_SAVING_TERMINATION_RATE =
            new BigDecimal("1.00");
    private static final long DEFAULT_DEPOSIT_MIN_AMOUNT = 10_000L;
    private static final long DEFAULT_DEPOSIT_MAX_AMOUNT = 5_000_000L;
    private static final long DEFAULT_SAVING_MIN_AMOUNT = 1_000L;
    private static final long DEFAULT_SAVING_MAX_AMOUNT = 500_000L;

    private final FinlifeClient finlifeClient;
    private final FinancialProductMapper financialProductMapper;

    public FinancialProductSyncService(
            FinlifeClient finlifeClient,
            FinancialProductMapper financialProductMapper) {
        this.finlifeClient = finlifeClient;
        this.financialProductMapper = financialProductMapper;
    }

    public boolean isConfigured() {
        return finlifeClient.isConfigured();
    }

    @Transactional
    public int syncDepositProducts() {
        FinlifeApiResponseDTO.Result result =
                finlifeClient.fetchDepositProducts();
        if (result == null) {
            throw new IllegalStateException("금융감독원 예금 상품 응답이 없습니다.");
        }
        List<FinlifeProductBaseDTO> bases = safe(result.getBaseList()).stream()
                .filter(this::validBase)
                .toList();
        List<FinlifeProductOptionDTO> optionList = safe(result.getOptionList());
        // 장애나 비정상 빈 응답으로 전체 예금이 판매 중지되는 것을 막는다.
        if (bases.isEmpty() || optionList.isEmpty()) {
            throw new IllegalStateException("금융감독원 예금 상품 응답이 비어 있습니다.");
        }

        Map<String, List<FinlifeProductOptionDTO>> options = groupOptions(optionList);
        // 아래 upsert가 이번 공시에 존재하는 상품만 다시 활성화한다.
        financialProductMapper.deactivateAllFinlifeDepositProducts();
        int affected = 0;
        for (FinlifeProductBaseDTO base : bases) {
            DepositProductVO product = depositProduct(
                    base, options.getOrDefault(key(base), List.of()));
            if (hasAnyRate(product)) {
                affected += financialProductMapper.upsertDepositProduct(product);
            }
        }
        return affected;
    }

    @Transactional
    public int syncSavingProducts() {
        FinlifeApiResponseDTO.Result result =
                finlifeClient.fetchSavingProducts();
        if (result == null) {
            throw new IllegalStateException("금융감독원 적금 상품 응답이 없습니다.");
        }
        List<FinlifeProductBaseDTO> bases = safe(result.getBaseList()).stream()
                .filter(this::validBase)
                .toList();
        List<FinlifeProductOptionDTO> optionList = safe(result.getOptionList());
        // 장애나 비정상 빈 응답으로 전체 적금이 판매 중지되는 것을 막는다.
        if (bases.isEmpty() || optionList.isEmpty()) {
            throw new IllegalStateException("금융감독원 적금 상품 응답이 비어 있습니다.");
        }

        Map<String, List<FinlifeProductOptionDTO>> options = groupOptions(optionList);
        // 아래 upsert가 이번 공시에 존재하는 옵션 조합만 다시 활성화한다.
        financialProductMapper.deactivateAllFinlifeSavingProducts();
        int affected = 0;
        for (FinlifeProductBaseDTO base : bases) {
            // 적립 유형과 이자 계산 방식이 다른 옵션은 각각 독립된 상품으로 저장한다.
            Map<SavingOptionProfile, List<FinlifeProductOptionDTO>> optionsByProfile =
                    safe(options.getOrDefault(key(base), List.of())).stream()
                            .filter(this::validOption)
                            .collect(Collectors.groupingBy(this::savingOptionProfile));
            for (Map.Entry<SavingOptionProfile, List<FinlifeProductOptionDTO>> entry
                    : optionsByProfile.entrySet()) {
                SavingProductVO product = savingProduct(
                        base, entry.getKey(), entry.getValue());
                if (hasAnyRate(product)) {
                    affected += financialProductMapper.upsertSavingProduct(product);
                }
            }
        }
        return affected;
    }

    private DepositProductVO depositProduct(
            FinlifeProductBaseDTO base,
            List<FinlifeProductOptionDTO> options) {
        DepositProductVO product = new DepositProductVO();
        applyBase(base, product);
        FinlifeProductOptionDTO representative = representativeOption(options);
        String interestRateType = normalizeInterestRateType(representative);
        product.setInterestCalculationType(
                interestCalculationType(interestRateType));
        applyRates(options,
                option -> interestRateType.equals(
                        normalizeInterestRateType(option)),
                product::setRate1m, product::setRate3m,
                product::setRate6m, product::setRate12m);
        product.setEarlyTerminationRate(DEFAULT_DEPOSIT_TERMINATION_RATE);
        product.setMinAmount(DEFAULT_DEPOSIT_MIN_AMOUNT);
        long maximum = base.getMaximumLimit() == null
                || base.getMaximumLimit() <= DEFAULT_DEPOSIT_MIN_AMOUNT
                ? DEFAULT_DEPOSIT_MAX_AMOUNT : base.getMaximumLimit();
        product.setMaxAmount(maximum);
        product.setDescription(depositDescription(
                product.getInterestCalculationType()));
        product.setActive(true);
        return product;
    }

    private SavingProductVO savingProduct(
            FinlifeProductBaseDTO base,
            SavingOptionProfile profile,
            List<FinlifeProductOptionDTO> options) {
        SavingProductVO product = new SavingProductVO();
        product.setFinancialCompanyCode(base.getFinancialCompanyCode());
        product.setFinancialProductCode(base.getFinancialProductCode());
        product.setFinancialCompanyName(base.getFinancialCompanyName());
        product.setName(base.getProductName());

        product.setSavingsType(savingsType(profile.reserveType()));
        product.setInterestCalculationType(
                interestCalculationType(profile.interestRateType()));
        applyRates(options,
                option -> profile.reserveType().equals(normalizeReserveType(option))
                        && profile.interestRateType().equals(
                        normalizeInterestRateType(option)),
                product::setRate1m, product::setRate3m,
                product::setRate6m, product::setRate12m);
        product.setEarlyTerminationRate(DEFAULT_SAVING_TERMINATION_RATE);
        product.setMinMonthAmount(DEFAULT_SAVING_MIN_AMOUNT);
        product.setMaxMonthAmount(DEFAULT_SAVING_MAX_AMOUNT);
        product.setDescription(savingDescription(
                product.getSavingsType(),
                product.getInterestCalculationType()));
        product.setActive(true);
        return product;
    }

    private void applyBase(
            FinlifeProductBaseDTO base,
            DepositProductVO product) {
        product.setFinancialCompanyCode(base.getFinancialCompanyCode());
        product.setFinancialProductCode(base.getFinancialProductCode());
        product.setFinancialCompanyName(base.getFinancialCompanyName());
        product.setName(base.getProductName());
    }

    private void applyRates(
            List<FinlifeProductOptionDTO> options,
            java.util.function.Predicate<FinlifeProductOptionDTO> filter,
            java.util.function.Consumer<BigDecimal> rate1m,
            java.util.function.Consumer<BigDecimal> rate3m,
            java.util.function.Consumer<BigDecimal> rate6m,
            java.util.function.Consumer<BigDecimal> rate12m) {
        Map<Integer, BigDecimal> rates = options.stream()
                .filter(filter)
                .filter(this::validOption)
                .collect(Collectors.toMap(
                        option -> Integer.parseInt(option.getSavingTerm()),
                        FinlifeProductOptionDTO::getInterestRate,
                        BigDecimal::max));
        rate1m.accept(rates.get(1));
        rate3m.accept(rates.get(3));
        rate6m.accept(rates.get(6));
        rate12m.accept(rates.get(12));
    }

    private FinlifeProductOptionDTO representativeOption(
            List<FinlifeProductOptionDTO> options) {
        return safe(options).stream()
                .filter(this::validOption)
                .max(Comparator
                        .comparing(FinlifeProductOptionDTO::getInterestRate)
                        .thenComparingInt(option -> Integer.parseInt(
                                option.getSavingTerm())))
                .orElse(null);
    }

    private String normalizeReserveType(FinlifeProductOptionDTO option) {
        return option != null
                && "F".equalsIgnoreCase(option.getReserveType()) ? "F" : "S";
    }

    private String normalizeInterestRateType(
            FinlifeProductOptionDTO option) {
        return option != null
                && "M".equalsIgnoreCase(option.getInterestRateType()) ? "M" : "S";
    }

    private String savingsType(String reserveType) {
        // 금융감독원 적립 유형 코드: S=정액적립식, F=자유적립식
        return "S".equals(reserveType) ? "FIXED" : "FREE";
    }

    private SavingOptionProfile savingOptionProfile(
            FinlifeProductOptionDTO option) {
        // 같은 외부 상품을 FIXED/FREE × SIMPLE/COMPOUND 조합으로 나누는 그룹 키다.
        return new SavingOptionProfile(
                normalizeReserveType(option),
                normalizeInterestRateType(option));
    }

    private String interestCalculationType(String interestRateType) {
        return "M".equals(interestRateType) ? "COMPOUND" : "SIMPLE";
    }

    private String depositDescription(String interestCalculationType) {
        if ("COMPOUND".equals(interestCalculationType)) {
            return "발생한 이자가 원금에 더해져 다음 이자에 반영되는 복리 예금";
        }
        return "이자를 원금에 합산하지 않고 계산하는 단리 예금";
    }

    private String savingDescription(
            String savingsType,
            String interestCalculationType) {
        String paymentDescription = "FIXED".equals(savingsType)
                ? "매월 정해진 금액을 납입하는"
                : "원하는 금액을 자유롭게 납입하는";
        String interestDescription = "COMPOUND".equals(
                interestCalculationType) ? "복리" : "단리";
        String typeDescription = "FIXED".equals(savingsType)
                ? "정액적금" : "자유적금";
        return paymentDescription + " " + interestDescription
                + " " + typeDescription;
    }

    private Map<String, List<FinlifeProductOptionDTO>> groupOptions(
            List<FinlifeProductOptionDTO> options) {
        return safe(options).stream()
                .filter(option -> option.getFinancialCompanyCode() != null
                        && option.getFinancialProductCode() != null)
                .collect(Collectors.groupingBy(this::key));
    }

    private boolean validBase(FinlifeProductBaseDTO base) {
        return base.getFinancialCompanyCode() != null
                && base.getFinancialProductCode() != null
                && base.getProductName() != null;
    }

    private boolean validOption(FinlifeProductOptionDTO option) {
        try {
            int term = Integer.parseInt(option.getSavingTerm());
            return SUPPORTED_TERMS.contains(term)
                    && option.getInterestRate() != null
                    && option.getInterestRate().signum() > 0;
        } catch (NumberFormatException | NullPointerException exception) {
            return false;
        }
    }

    private boolean hasAnyRate(DepositProductVO product) {
        return product.getRate1m() != null || product.getRate3m() != null
                || product.getRate6m() != null || product.getRate12m() != null;
    }

    private boolean hasAnyRate(SavingProductVO product) {
        return product.getRate1m() != null || product.getRate3m() != null
                || product.getRate6m() != null || product.getRate12m() != null;
    }

    private String key(FinlifeProductBaseDTO base) {
        return base.getFinancialCompanyCode() + "_"
                + base.getFinancialProductCode();
    }

    private String key(FinlifeProductOptionDTO option) {
        return option.getFinancialCompanyCode() + "_"
                + option.getFinancialProductCode();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    /** 금융감독원 적립 유형과 이자 계산 방식의 조합을 나타낸다. */
    private record SavingOptionProfile(
            String reserveType,
            String interestRateType) {
    }
}
