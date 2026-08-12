package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.vo.DepositProductVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.SavingProductVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FinancialProductRateCalculator {

    public BigDecimal depositRate(DepositProductVO product, int termMonths,
                                  BigDecimal bonusRate) {
        return addBonus(rate(product.getRate1m(), product.getRate3m(),
                product.getRate6m(), product.getRate12m(), termMonths), bonusRate);
    }

    public BigDecimal savingRate(SavingProductVO product, int termMonths,
                                 BigDecimal bonusRate) {
        return addBonus(rate(product.getRate1m(), product.getRate3m(),
                product.getRate6m(), product.getRate12m(), termMonths), bonusRate);
    }

    private BigDecimal rate(BigDecimal rate1m, BigDecimal rate3m,
                            BigDecimal rate6m, BigDecimal rate12m,
                            int termMonths) {
        BigDecimal result = switch (termMonths) {
            case 1 -> rate1m;
            case 3 -> rate3m;
            case 6 -> rate6m;
            case 12 -> rate12m;
            default -> null;
        };
        if (result == null) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_INVALID_TERM);
        }
        return result;
    }

    private BigDecimal addBonus(BigDecimal baseRate, BigDecimal bonusRate) {
        return baseRate.add(bonusRate == null ? BigDecimal.ZERO : bonusRate);
    }
}
