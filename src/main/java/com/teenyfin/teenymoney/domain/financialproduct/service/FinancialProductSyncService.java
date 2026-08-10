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
        Map<String, List<FinlifeProductOptionDTO>> options =
                groupOptions(result.getOptionList());
        int affected = 0;
        for (FinlifeProductBaseDTO base : safe(result.getBaseList())) {
            if (!validBase(base)) {
                continue;
            }
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
        Map<String, List<FinlifeProductOptionDTO>> options =
                groupOptions(result.getOptionList());
        int affected = 0;
        for (FinlifeProductBaseDTO base : safe(result.getBaseList())) {
            if (!validBase(base)) {
                continue;
            }
            SavingProductVO product = savingProduct(
                    base, options.getOrDefault(key(base), List.of()));
            if (hasAnyRate(product)) {
                affected += financialProductMapper.upsertSavingProduct(product);
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
        product.setMinTeenyScore(0);
        product.setDescription("금융감독원 금융상품통합비교공시 연동 상품");
        product.setActive(true);
        return product;
    }

    private SavingProductVO savingProduct(
            FinlifeProductBaseDTO base,
            List<FinlifeProductOptionDTO> options) {
        SavingProductVO product = new SavingProductVO();
        product.setFinancialCompanyCode(base.getFinancialCompanyCode());
        product.setFinancialProductCode(base.getFinancialProductCode());
        product.setFinancialCompanyName(base.getFinancialCompanyName());
        product.setName(base.getProductName());

        FinlifeProductOptionDTO representative = representativeOption(options);
        String reserveType = normalizeReserveType(representative);
        String interestRateType = normalizeInterestRateType(representative);
        product.setSavingsType(savingsType(reserveType));
        product.setInterestCalculationType(
                interestCalculationType(interestRateType));
        applyRates(options,
                option -> reserveType.equals(normalizeReserveType(option))
                        && interestRateType.equals(
                        normalizeInterestRateType(option)),
                product::setRate1m, product::setRate3m,
                product::setRate6m, product::setRate12m);
        product.setEarlyTerminationRate(DEFAULT_SAVING_TERMINATION_RATE);
        product.setMinMonthAmount(DEFAULT_SAVING_MIN_AMOUNT);
        product.setMaxMonthAmount(DEFAULT_SAVING_MAX_AMOUNT);
        product.setMinTeenyScore(0);
        product.setDescription("금융감독원 금융상품통합비교공시 연동 상품");
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
        return "F".equals(reserveType) ? "FIXED" : "FREE";
    }

    private String interestCalculationType(String interestRateType) {
        return "M".equals(interestRateType) ? "COMPOUND" : "SIMPLE";
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
}
