package com.teenyfin.teenymoney.domain.financialproduct.vo;

import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.global.exception.BusinessException;

import java.util.Locale;

public enum FinancialProductType {
    DEPOSIT,
    SAVING,
    LOAN;

    public static FinancialProductType from(String value) {
        if (value == null) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_TYPE_INVALID);
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    FinancialProductErrorCode.FINANCIAL_PRODUCT_TYPE_INVALID);
        }
    }
}
