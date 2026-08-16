package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.vo.LoanRepaymentVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 대출 상환 방식별 회차 원금과 이자를 원 단위로 계산한다. */
@Component
public class LoanRepaymentCalculator {
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /**
     * 이번 회차의 약정 원금과 현재 잔액 기준 이자를 계산한다.
     * 마지막 회차에는 반올림으로 남은 금액과 과거 미납 원금을 함께 없애기 위해
     * 실제 미상환 원금 전액을 상환 예정 원금으로 사용한다.
     */
    public Installment calculate(LoanRepaymentVO loan, int installmentNo) {
        if (installmentNo < 1 || installmentNo > loan.getTermMonths()) {
            throw new IllegalArgumentException("상환 회차가 약정 기간을 벗어났습니다.");
        }
        long principal = installmentNo == loan.getTermMonths()
                ? loan.getOutstandingPrincipal()
                : scheduledPrincipal(loan, installmentNo);
        long interest = monthlyInterest(
                loan.getOutstandingPrincipal(), loan.getAppliedRate());
        return new Installment(principal, interest);
    }

    /**
     * 해당 회차까지 정상 납부했을 때의 계약상 잔액이다.
     * 별도 연체 원금 컬럼을 추가하지 않고도 실제 잔액과 비교하여 미납 원금을 판별한다.
     */
    public long expectedOutstandingAfter(LoanRepaymentVO loan, int installmentNo) {
        if ("BULLET".equals(loan.getRepaymentType())) {
            return installmentNo >= loan.getTermMonths() ? 0L : loan.getPrincipalAmount();
        }
        long paidBySchedule = 0L;
        for (int no = 1; no <= Math.min(installmentNo, loan.getTermMonths()); no++) {
            long scheduled = "EQUAL_PRINCIPAL_INTEREST".equals(loan.getRepaymentType())
                    ? equalPrincipalInterestPrincipal(loan, no)
                    : equalPrincipal(loan.getPrincipalAmount(), loan.getTermMonths(), no);
            paidBySchedule = Math.addExact(paidBySchedule, scheduled);
        }
        return Math.max(0L, loan.getPrincipalAmount() - paidBySchedule);
    }

    private long scheduledPrincipal(LoanRepaymentVO loan, int installmentNo) {
        return switch (loan.getRepaymentType()) {
            case "EQUAL_PRINCIPAL" -> equalPrincipal(
                    loan.getPrincipalAmount(), loan.getTermMonths(), installmentNo);
            case "EQUAL_PRINCIPAL_INTEREST" -> {
                yield Math.min(loan.getOutstandingPrincipal(),
                        equalPrincipalInterestPrincipal(loan, installmentNo));
            }
            case "BULLET" -> 0L;
            default -> throw new IllegalArgumentException("지원하지 않는 대출 상환 방식입니다.");
        };
    }

    private long equalPrincipal(long principal, int termMonths, int installmentNo) {
        long base = principal / termMonths;
        return installmentNo == termMonths
                ? principal - base * (termMonths - 1L) : base;
    }

    private long equalPrincipalInterestPayment(
            long principal, BigDecimal annualRate, int termMonths) {
        // 원리금균등 월 납입액은 약정 시점의 원금·금리·기간으로 고정하고 원 단위 미만은 버린다.
        double monthlyRate = annualRate.doubleValue() / 100.0 / 12.0;
        if (monthlyRate == 0.0) return principal / termMonths;
        double factor = Math.pow(1.0 + monthlyRate, termMonths);
        return BigDecimal.valueOf(principal * monthlyRate * factor / (factor - 1.0))
                .setScale(0, RoundingMode.DOWN).longValueExact();
    }

    private long equalPrincipalInterestPrincipal(
            LoanRepaymentVO loan, int installmentNo) {
        long payment = equalPrincipalInterestPayment(
                loan.getPrincipalAmount(), loan.getAppliedRate(), loan.getTermMonths());
        long contractualOutstanding = loan.getPrincipalAmount();
        for (int no = 1; no <= installmentNo; no++) {
            long principal = no == loan.getTermMonths()
                    ? contractualOutstanding
                    : Math.max(0L, payment - monthlyInterest(
                            contractualOutstanding, loan.getAppliedRate()));
            if (no == installmentNo) return Math.min(contractualOutstanding, principal);
            contractualOutstanding -= Math.min(contractualOutstanding, principal);
        }
        throw new IllegalArgumentException("상환 회차가 약정 기간을 벗어났습니다.");
    }

    private long monthlyInterest(long principal, BigDecimal annualRate) {
        if (principal <= 0) return 0L;
        return BigDecimal.valueOf(principal)
                .multiply(annualRate)
                .divide(ONE_HUNDRED, 12, RoundingMode.DOWN)
                .divide(MONTHS_PER_YEAR, 0, RoundingMode.DOWN)
                .longValueExact();
    }

    public record Installment(long principal, long interest) {
        public long total() {
            return Math.addExact(principal, interest);
        }
    }
}
