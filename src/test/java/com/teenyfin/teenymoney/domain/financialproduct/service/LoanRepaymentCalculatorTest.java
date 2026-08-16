package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanRepaymentVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LoanRepaymentCalculatorTest {
    private final LoanRepaymentCalculator calculator = new LoanRepaymentCalculator();

    @Test
    @DisplayName("원금균등상환은 매월 같은 원금을 청구하고 마지막 회차가 나눗셈 나머지를 정산한다")
    void equalPrincipalUsesLastInstallmentForRemainder() {
        LoanRepaymentVO loan = loan(100_000L, 3, "EQUAL_PRINCIPAL");

        assertThat(calculator.calculate(loan, 1).principal()).isEqualTo(33_333L);
        loan.setOutstandingPrincipal(33_334L);
        assertThat(calculator.calculate(loan, 3).principal()).isEqualTo(33_334L);
    }

    @Test
    @DisplayName("만기일시상환은 중간 회차에는 이자만, 마지막 회차에는 남은 원금 전부를 청구한다")
    void bulletRepaysPrincipalAtLastInstallment() {
        LoanRepaymentVO loan = loan(100_000L, 3, "BULLET");

        assertThat(calculator.calculate(loan, 1).principal()).isZero();
        assertThat(calculator.calculate(loan, 3).principal()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("원리금균등상환은 회차가 진행될수록 이자는 줄고 상환 원금은 증가한다")
    void equalPrincipalInterestAmortizesBalance() {
        LoanRepaymentVO loan = loan(120_000L, 12, "EQUAL_PRINCIPAL_INTEREST");

        long firstPrincipal = calculator.calculate(loan, 1).principal();
        loan.setOutstandingPrincipal(calculator.expectedOutstandingAfter(loan, 5));
        long sixthPrincipal = calculator.calculate(loan, 6).principal();

        assertThat(sixthPrincipal).isGreaterThan(firstPrincipal);
    }

    private LoanRepaymentVO loan(long principal, int term, String type) {
        LoanRepaymentVO loan = new LoanRepaymentVO();
        loan.setPrincipalAmount(principal);
        loan.setOutstandingPrincipal(principal);
        loan.setAppliedRate(new BigDecimal("7.00"));
        loan.setTermMonths(term);
        loan.setRepaymentType(type);
        return loan;
    }
}
