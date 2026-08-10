package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.domain.teenyscore.dto.request.TeenyScoreChangeRequestDTO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreEventCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 결제·예금·적금·대출 이벤트를 티니점수 정책으로 변환한다.
 * 점수를 직접 저장하지 않고 공통 변경 로직에 전달할 요청을 만든다.
 */
@Service
public class TeenyScorePolicyService {

    private static final int CONSECUTIVE_MATURITY_BONUS = 10;  // 6개월 이상 예·적금을 2회 연속 정상 만기한 경우 적용하는 추가 점수
    private static final int REPEATED_EARLY_TERMINATION_PENALTY = -8;   // 최근 3개월 내 예·적금 중도해지가 3회 이상 누적된 경우 적용하는 감점
    private static final int LOAN_MATURITY_SCORE = 6;         // 대출 만기일까지 원금과 이자를 모두 정상 상환한 경우 적용하는 점수
    private static final int LOAN_DEFAULT_PENALTY = -20;   // 월별 미납 감점과 별도로 최종 미상환 사건에 한 번 적용하는 감점

    // 예금 중도해지 감점표
    // 행: 가입 기간 순서 1개월, 3개월, 6개월, 12개월
    // 열: 상품 진행률 순서 0~24%, 25~49%, 50~74%, 75~99%
    private static final int[][] DEPOSIT_EARLY_TERMINATION_SCORES = {
            {-3, -2, -1, -1}, {-5, -3, -2, -1},
            {-6, -5, -3, -2}, {-8, -6, -4, -2}
    };
    // 정액적금 중도해지 및 최종 납입률 70% 미만 만기 감점표
    // 행: 가입 기간 순서 1개월, 3개월, 6개월, 12개월
    // 열: 상품 진행률 순서 0~24%, 25~49%, 50~74%, 75~99%
    private static final int[][] FIXED_SAVING_EARLY_TERMINATION_SCORES = {
            {-3, -2, -1, -1}, {-6, -4, -2, -1},
            {-9, -6, -4, -2}, {-11, -7, -5, -2}
    };
    // 자유적금 중도해지 및 최종 납입률 70% 미만 만기 감점표
    // 행: 가입 기간 순서 1개월, 3개월, 6개월, 12개월
    // 열: 상품 진행률 순서 0~24%, 25~49%, 50~74%, 75~99%
    private static final int[][] FREE_SAVING_EARLY_TERMINATION_SCORES = {
            {-2, -2, -1, -1}, {-5, -3, -2, -1},
            {-8, -5, -3, -2}, {-11, -7, -4, -2}
    };

    // WATCH 업종의 월 이용 횟수가 부모 설정 기준을 넘었는지 판정한다.
    public TeenyScoreChangeRequestDTO watchPayment(
            Long childId,
            Long paymentId,
            int monthlyCount,
            int thresholdCount) {
        requireNonNegative(monthlyCount, "monthlyCount");
        requireNonNegative(thresholdCount, "thresholdCount");

        boolean exceeded = monthlyCount > thresholdCount;
        return request(
                childId,
                exceeded
                        ? TeenyScoreEventCode.PAYMENT_WATCH_OVER_THRESHOLD // 부모가 설정한 월 이용 기준을 초과한 WATCH 업종 결제
                        : TeenyScoreEventCode.PAYMENT_WATCH_WITHIN_THRESHOLD, // 부모가 설정한 월 이용 기준 이내의 WATCH 업종 결제
                exceeded ? -2 : -1,
                "PAYMENT_WATCH:" + paymentId,
                exceeded
                        ? "WATCH 업종 기준 초과 결제"
                        : "WATCH 업종 기준 이내 결제",
                "PAYMENT",
                paymentId);
    }

    /** BLOCK 결제 시도는 결제를 거절하고, 자녀별 하루 최초 1회에만 감점한다. */
    public TeenyScoreChangeRequestDTO blockPayment(
            Long childId,
            Long paymentId,
            LocalDate paymentDate) {
        if (paymentDate == null) {
            throw new IllegalArgumentException("paymentDate는 필수입니다.");
        }
        return request(
                childId,
                TeenyScoreEventCode.PAYMENT_BLOCKED,
                -20,
                "PAYMENT_BLOCKED:" + paymentDate,
                "BLOCK 업종 결제 시도",
                "PAYMENT",
                paymentId);
    }

    // ========================================
    // 예금 정책
    // ========================================

    public TeenyScoreChangeRequestDTO depositMaturity(
            Long childId,
            Long depositEnrollmentId,
            int termMonths) {
        return request(
                childId,
                TeenyScoreEventCode.DEPOSIT_MATURED, // 예금을 중도해지하지 않고 정상 만기까지 유지
                depositMaturityScore(termMonths),
                "DEPOSIT_MATURED:" + depositEnrollmentId,
                "예금 만기 달성",
                "DEPOSIT_ENROLLMENT",
                depositEnrollmentId);
    }

    public TeenyScoreChangeRequestDTO depositEarlyTermination(
            Long childId,
            Long depositEnrollmentId,
            int termMonths,
            int progressPercent) {
        return request(
                childId,
                TeenyScoreEventCode.DEPOSIT_EARLY_TERMINATED, // 예금을 약정 만기 전에 중도해지
                earlyTerminationScore(
                        termMonths,
                        progressPercent,
                        DEPOSIT_EARLY_TERMINATION_SCORES),
                "DEPOSIT_TERMINATED:" + depositEnrollmentId,
                "예금 중도해지",
                "DEPOSIT_ENROLLMENT",
                depositEnrollmentId);
    }

    // ========================================
    // 적금 정책
    // ========================================

    public TeenyScoreChangeRequestDTO fixedSavingInstallment(
            Long childId,
            Long savingEnrollmentId,
            int installmentNo,
            boolean paid) {
        if (installmentNo <= 0) {
            throw new IllegalArgumentException("installmentNo는 1 이상이어야 합니다.");
        }

        TeenyScoreEventCode eventCode = paid
                ? TeenyScoreEventCode.SAVING_FIXED_INSTALLMENT_PAID // 정액적금 약정 회차 납입 성공
                : TeenyScoreEventCode.SAVING_FIXED_INSTALLMENT_MISSED; // 정액적금 약정 회차 미납
        return request(
                childId,
                eventCode,
                paid ? 4 : -2,
                "SAVING_FIXED_" + (paid ? "PAID:" : "MISSED:")
                        + savingEnrollmentId + ":" + installmentNo,
                paid ? "정액적립식 적금 정기 납입 성공" : "정액적립식 적금 정기 납입 실패",
                "SAVING_ENROLLMENT",
                savingEnrollmentId);
    }

    public TeenyScoreChangeRequestDTO fixedSavingMaturity(
            Long childId,
            Long savingEnrollmentId,
            int termMonths,
            int paymentRate,
            int firstMissedProgressPercent) {
        requirePercentage(paymentRate, "paymentRate");
        boolean normallyMatured = paymentRate >= 70;
        int amount = normallyMatured
                ? fixedSavingMaturityScore(termMonths)
                : earlyTerminationScore(termMonths, firstMissedProgressPercent,
                FIXED_SAVING_EARLY_TERMINATION_SCORES);
        return request(
                childId,
                normallyMatured
                        ? TeenyScoreEventCode.SAVING_FIXED_MATURED // 정액적금의 최종 납입률 조건을 충족한 정상 만기
                        : TeenyScoreEventCode.SAVING_EARLY_TERMINATED, // 정액적금의 최종 납입률 조건을 충족하지 못한 만기
                amount,
                "SAVING_FIXED_MATURED:" + savingEnrollmentId,
                normallyMatured
                        ? "정액적립식 적금 만기 달성"
                        : "정액적립식 적금 70% 미만 만기",
                "SAVING_ENROLLMENT",
                savingEnrollmentId);
    }

    /** 최종 납입률 70% 미만이면 최초 월 목표 미달 시점 기준으로 중도해지 감점을 적용한다. */
    public TeenyScoreChangeRequestDTO freeSavingMaturity(
            Long childId,
            Long savingEnrollmentId,
            int termMonths,
            int totalPaymentRate,
            int firstShortfallProgressPercent) {
        requirePercentage(totalPaymentRate, "totalPaymentRate");
        requireSupportedTerm(termMonths);
        boolean normallyMatured = totalPaymentRate >= 70;
        int amount = normallyMatured
                ? 0
                : earlyTerminationScore(termMonths, firstShortfallProgressPercent,
                        FREE_SAVING_EARLY_TERMINATION_SCORES);
        return request(
                childId,
                normallyMatured
                        ? TeenyScoreEventCode.SAVING_FREE_MATURED // 자유적금의 최종 납입률 조건을 충족한 정상 만기
                        : TeenyScoreEventCode.SAVING_EARLY_TERMINATED, // 자유적금의 최종 납입률 조건을 충족하지 못한 만기
                amount,
                "SAVING_FREE_MATURED:" + savingEnrollmentId,
                normallyMatured
                        ? "자유적립식 적금 만기 달성"
                        : "자유적립식 적금 70% 미만 만기",
                "SAVING_ENROLLMENT",
                savingEnrollmentId);
    }

    public TeenyScoreChangeRequestDTO freeSavingMonthlyResult(
            Long childId,
            Long savingEnrollmentId,
            YearMonth targetMonth,
            int paymentRate) {
        if (targetMonth == null) {
            throw new IllegalArgumentException("targetMonth는 필수입니다.");
        }
        requireNonNegative(paymentRate, "paymentRate");
        return request(
                childId,
                TeenyScoreEventCode.SAVING_FREE_MONTHLY_RESULT, // 자유적금의 해당 월 누적 납입률 평가 결과
                freeSavingMonthlyScore(paymentRate),
                "SAVING_FREE_MONTHLY:" + savingEnrollmentId + ":" + targetMonth,
                "자유적립식 적금 월 납입률 확정",
                "SAVING_ENROLLMENT",
                savingEnrollmentId);
    }

    public TeenyScoreChangeRequestDTO savingEarlyTermination(
            Long childId,
            Long savingEnrollmentId,
            boolean fixedSaving,
            int termMonths,
            int progressPercent) {
        int[][] scores = fixedSaving
                ? FIXED_SAVING_EARLY_TERMINATION_SCORES
                : FREE_SAVING_EARLY_TERMINATION_SCORES;
        return request(
                childId,
                TeenyScoreEventCode.SAVING_EARLY_TERMINATED, // 정액적금 또는 자유적금을 약정 만기 전에 중도해지
                earlyTerminationScore(termMonths, progressPercent, scores),
                "SAVING_TERMINATED:" + savingEnrollmentId,
                fixedSaving
                        ? "정액적립식 적금 중도해지"
                        : "자유적립식 적금 중도해지",
                "SAVING_ENROLLMENT",
                savingEnrollmentId);
    }

    public TeenyScoreChangeRequestDTO consecutiveMaturityBonus(
            Long childId,
            Long referenceId) {
        return request(
                childId,
                TeenyScoreEventCode.SAVING_CONSECUTIVE_MATURITY_BONUS, // 장기 예·적금을 연속으로 정상 만기 달성
                CONSECUTIVE_MATURITY_BONUS,
                "SAVING_CONSECUTIVE_MATURITY:" + referenceId,
                "6개월 이상 예·적금 만기 2회 연속 달성",
                "SAVING_ENROLLMENT",
                referenceId);
    }

    public TeenyScoreChangeRequestDTO repeatedEarlyTermination(
            Long childId,
            Long referenceId) {
        return request(
                childId,
                TeenyScoreEventCode.SAVING_REPEATED_EARLY_TERMINATION, // 최근 기간 내 예·적금 중도해지가 반복해서 누적
                REPEATED_EARLY_TERMINATION_PENALTY,
                "REPEATED_EARLY_TERMINATION:" + referenceId,
                "최근 3개월 내 중도해지 3회 누적",
                "SAVING_ENROLLMENT",
                referenceId);
    }

    // ========================================
    // 대출 정책
    // ========================================

    public TeenyScoreChangeRequestDTO loanOverdue(
            Long childId,
            Long loanEnrollmentId,
            YearMonth targetMonth,
            long previousUnpaidAmount,
            long currentScheduledAmount,
            long paidAmount) {
        if (targetMonth == null) {
            throw new IllegalArgumentException("targetMonth는 필수입니다.");
        }
        requireNonNegative(previousUnpaidAmount, "previousUnpaidAmount");
        if (currentScheduledAmount <= 0) {
            throw new IllegalArgumentException("currentScheduledAmount는 0보다 커야 합니다.");
        }
        requireNonNegative(paidAmount, "paidAmount");
        long totalRequiredAmount = Math.addExact(
                previousUnpaidAmount, currentScheduledAmount);
        return request(
                childId,
                TeenyScoreEventCode.LOAN_INSTALLMENT_OVERDUE, // 대출 회차 상환일까지 예정 금액을 전부 상환하지 못한 결과
                loanOverdueScore(totalRequiredAmount, paidAmount),
                "LOAN_OVERDUE:" + loanEnrollmentId + ":" + targetMonth,
                "대출 월별 상환 결과",
                "LOAN_ENROLLMENT",
                loanEnrollmentId);
    }

    public TeenyScoreChangeRequestDTO loanMaturity(
            Long childId,
            Long loanEnrollmentId) {
        return request(
                childId,
                TeenyScoreEventCode.LOAN_MATURED_REPAID, // 대출 만기일까지 원금과 이자를 모두 정상 상환
                LOAN_MATURITY_SCORE,
                "LOAN_REPAID:" + loanEnrollmentId,
                "대출 정상 만기 완납",
                "LOAN_ENROLLMENT",
                loanEnrollmentId);
    }

    public TeenyScoreChangeRequestDTO loanDefault(
            Long childId,
            Long loanEnrollmentId) {
        return request(
                childId,
                TeenyScoreEventCode.LOAN_DEFAULTED, // 만기 후 유예기간이 끝나도 미상환 금액이 남은 상태
                LOAN_DEFAULT_PENALTY,
                "LOAN_DEFAULTED:" + loanEnrollmentId,
                "대출 최종 만기 미상환",
                "LOAN_ENROLLMENT",
                loanEnrollmentId);
    }

    private int depositMaturityScore(int termMonths) {
        return switch (termMonths) {
            case 1 -> 6;
            case 3 -> 19;
            case 6 -> 39;
            case 12 -> 79;
            default -> throw unsupportedTerm(termMonths);
        };
    }

    private int fixedSavingMaturityScore(int termMonths) {
        return switch (termMonths) {
            case 1 -> 3;
            case 3 -> 10;
            case 6 -> 21;
            case 12 -> 43;
            default -> throw unsupportedTerm(termMonths);
        };
    }

    private int freeSavingMonthlyScore(int paymentRate) {
        if (paymentRate == 0) {
            return 0;
        }
        if (paymentRate < 30) {
            return 2;
        }
        if (paymentRate < 60) {
            return 4;
        }
        if (paymentRate < 100) {
            return 6;
        }
        return 8;
    }

    // 약정 기간과 상품 진행률 구간으로 예·적금 중도해지 점수를 찾는다.
    private int earlyTerminationScore(
            int termMonths,
            int progressPercent,
            int[][] scores) {
        requirePercentage(progressPercent, "progressPercent");
        if (progressPercent >= 100) {
            throw new IllegalArgumentException(
                    "중도해지 진행률은 100% 미만이어야 합니다.");
        }
        int termIndex = switch (termMonths) {
            case 1 -> 0;
            case 3 -> 1;
            case 6 -> 2;
            case 12 -> 3;
            default -> throw unsupportedTerm(termMonths);
        };
        int progressIndex;
        if (progressPercent < 25) {
            progressIndex = 0;
        } else if (progressPercent < 50) {
            progressIndex = 1;
        } else if (progressPercent < 75) {
            progressIndex = 2;
        } else {
            progressIndex = 3;
        }
        return scores[termIndex][progressIndex];
    }

    // 실제 납부액을 전월 미납금과 당월 예정액의 합으로 나눈 비율로 감점을 계산한다.
    private int loanOverdueScore(
            long scheduledAmount,
            long paidAmount) {
        if (paidAmount >= scheduledAmount) {
            return 0;
        }
        int paymentRate = BigDecimal.valueOf(paidAmount)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(scheduledAmount),
                        0,
                        RoundingMode.DOWN)
                .intValueExact();
        if (paymentRate >= 75) {
            return -2;
        }
        if (paymentRate >= 50) {
            return -4;
        }
        if (paymentRate >= 25) {
            return -6;
        }
        return -8;
    }

    // 모든 정책에서 동일한 형식의 변경 요청과 멱등성 키를 사용하도록 모은다.
    private TeenyScoreChangeRequestDTO request(
            Long childId,
            TeenyScoreEventCode eventCode,
            int amount,
            String eventKey,
            String description,
            String referenceType,
            Long referenceId) {
        if (childId == null || referenceId == null) {
            throw new IllegalArgumentException(
                    "childId와 referenceId는 필수입니다.");
        }
        return new TeenyScoreChangeRequestDTO(
                childId,
                eventCode,
                amount,
                eventKey,
                description,
                referenceType,
                referenceId);
    }

    private void requirePercentage(int value, String fieldName) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(
                    fieldName + "는 0 이상 100 이하여야 합니다.");
        }
    }

    private void requireNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName + "는 0 이상이어야 합니다.");
        }
    }

    private IllegalArgumentException unsupportedTerm(int termMonths) {
        return new IllegalArgumentException(
                "지원하지 않는 가입 기간입니다: " + termMonths);
    }

    private void requireSupportedTerm(int termMonths) {
        if (termMonths != 1 && termMonths != 3
                && termMonths != 6 && termMonths != 12) {
            throw unsupportedTerm(termMonths);
        }
    }
}
