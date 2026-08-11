package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.domain.teenyscore.dto.request.TeenyScoreChangeRequestDTO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreEventCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("티니점수 정책 서비스")
class TeenyScorePolicyServiceTest {

    private TeenyScorePolicyService teenyScorePolicyService;

    @BeforeEach
    void setUp() {
        teenyScorePolicyService = new TeenyScorePolicyService();
    }

    @Test
    @DisplayName("WATCH 결제는 기준 이내 -1점, 초과 -2점을 적용한다")
    void watchPaymentUsesThresholdBoundaryAndCreatesEventMetadata() {
        TeenyScoreChangeRequestDTO withinThreshold =
                teenyScorePolicyService.watchPayment(2L, 100L, 3, 3);
        TeenyScoreChangeRequestDTO overThreshold =
                teenyScorePolicyService.watchPayment(2L, 101L, 4, 3);

        assertEquals(-1, withinThreshold.getAmount());
        assertEquals(
                TeenyScoreEventCode.PAYMENT_WATCH_WITHIN_THRESHOLD,
                withinThreshold.getEventCode());
        assertEquals("PAYMENT_WATCH:100", withinThreshold.getEventKey());
        assertEquals("PAYMENT", withinThreshold.getReferenceType());
        assertEquals(100L, withinThreshold.getReferenceId());

        assertEquals(-2, overThreshold.getAmount());
        assertEquals(
                TeenyScoreEventCode.PAYMENT_WATCH_OVER_THRESHOLD,
                overThreshold.getEventCode());

        printResult("WATCH 결제",
                "기준 3회 / 3회=-1점 / 4회=-2점 / eventKey="
                        + withinThreshold.getEventKey());
    }

    @Test
    void blockPaymentAppliesDailyPenaltyAndCreatesDailyEventKey() {
        TeenyScoreChangeRequestDTO request =
                teenyScorePolicyService.blockPayment(
                        2L, 100L, LocalDate.of(2026, 8, 10));

        assertEquals(-20, request.getAmount());
        assertEquals(TeenyScoreEventCode.PAYMENT_BLOCKED,
                request.getEventCode());
        assertEquals("PAYMENT_BLOCKED:2026-08-10", request.getEventKey());
        assertEquals("PAYMENT", request.getReferenceType());
        assertEquals(100L, request.getReferenceId());
    }

    @Test
    @DisplayName("예금 만기는 가입 기간별 점수를 적용한다")
    void depositMaturityUsesTermScoreTable() {
        int[] terms = {1, 3, 6, 12};
        int[] expectedScores = {6, 19, 39, 79};

        for (int index = 0; index < terms.length; index++) {
            assertEquals(
                    expectedScores[index],
                    teenyScorePolicyService.depositMaturity(
                            2L,
                            (long) index + 1,
                            terms[index]).getAmount());
        }

        printResult("예금 만기", "1개월=+6 / 3개월=+19 / 6개월=+39 / 12개월=+79");
    }

    @Test
    @DisplayName("예금 중도해지는 모든 기간과 진행률 경계별 감점을 적용한다")
    void depositEarlyTerminationUsesTermAndProgressTable() {
        int[] terms = {1, 3, 6, 12};
        int[] progressBoundaries = {0, 24, 25, 49, 50, 74, 75, 99};
        int[][] expectedScores = {
                {-3, -3, -2, -2, -1, -1, -1, -1},
                {-5, -5, -3, -3, -2, -2, -1, -1},
                {-6, -6, -5, -5, -3, -3, -2, -2},
                {-8, -8, -6, -6, -4, -4, -2, -2}
        };

        for (int termIndex = 0; termIndex < terms.length; termIndex++) {
            for (int progressIndex = 0;
                    progressIndex < progressBoundaries.length;
                    progressIndex++) {
                assertEquals(
                        expectedScores[termIndex][progressIndex],
                        teenyScorePolicyService.depositEarlyTermination(
                                2L,
                                (long) termIndex + 1,
                                terms[termIndex],
                                progressBoundaries[progressIndex]).getAmount());
            }
        }

        printResult("예금 중도해지",
                "1·3·6·12개월 × 진행률 0·24·25·49·50·74·75·99% 검증 완료");
    }

    @Test
    @DisplayName("정액적금 납입 성공은 +4점, 실패는 -2점이다")
    void fixedSavingInstallmentUsesPaidAndMissedScores() {
        TeenyScoreChangeRequestDTO paid =
                teenyScorePolicyService.fixedSavingInstallment(
                        2L, 10L, 3, true);
        TeenyScoreChangeRequestDTO missed =
                teenyScorePolicyService.fixedSavingInstallment(
                        2L, 10L, 3, false);

        assertEquals(4, paid.getAmount());
        assertEquals(
                TeenyScoreEventCode.SAVING_FIXED_INSTALLMENT_PAID,
                paid.getEventCode());
        assertEquals("SAVING_FIXED_PAID:10:3", paid.getEventKey());

        assertEquals(-2, missed.getAmount());
        assertEquals(
                TeenyScoreEventCode.SAVING_FIXED_INSTALLMENT_MISSED,
                missed.getEventCode());
        assertEquals("SAVING_FIXED_MISSED:10:3", missed.getEventKey());

        printResult("정액적금 납입", "성공=+4점 / 실패=-2점 / 3회차 eventKey 검증 완료");
    }

    @Test
    @DisplayName("정액적금 만기 납입률 70% 미만은 정액적금 중도해지 감점을 적용한다")
    void fixedSavingMaturityRequiresSeventyPercentPaymentRate() {
        assertEquals(
                -5,
                teenyScorePolicyService.fixedSavingMaturity(
                        2L, 10L, 12, 69, 50).getAmount());
        assertEquals(
                43,
                teenyScorePolicyService.fixedSavingMaturity(
                        2L, 11L, 12, 70, 50).getAmount());

        printResult("정액적금 만기 경계", "납입률 69%=-5점 / 70%=+43점");
    }

    @Test
    @DisplayName("정액적금 만기는 가입 기간별 보너스를 적용한다")
    void fixedSavingMaturityUsesTermScoreTable() {
        int[] terms = {1, 3, 6, 12};
        int[] expectedScores = {3, 10, 21, 43};

        for (int index = 0; index < terms.length; index++) {
            assertEquals(
                    expectedScores[index],
                    teenyScorePolicyService.fixedSavingMaturity(
                            2L,
                            (long) index + 1,
                            terms[index],
                            70,
                            0).getAmount());
        }

        printResult("정액적금 만기", "1개월=+3 / 3개월=+10 / 6개월=+21 / 12개월=+43");
    }

    @Test
    @DisplayName("정액적금 납입률이 70% 미만이면 정액적금 중도해지 점수표를 적용한다")
    void fixedSavingBelowSeventyPercentUsesFixedSavingPenalty() {
        TeenyScoreChangeRequestDTO result =
                teenyScorePolicyService.fixedSavingMaturity(
                        2L,
                        100L,
                        3,
                        60,
                        20);

        assertEquals(
                TeenyScoreEventCode.SAVING_EARLY_TERMINATED,
                result.getEventCode());
        assertEquals(-6, result.getAmount());
    }

    @Test
    @DisplayName("자유적금은 월 납입률 구간별 점수를 적용한다")
    void freeSavingMonthlyResultUsesPaymentRateBoundaries() {
        int[] paymentRates = {0, 1, 29, 30, 59, 60, 99, 100, 120};
        int[] expectedScores = {0, 2, 2, 4, 4, 6, 6, 8, 8};
        YearMonth targetMonth = YearMonth.of(2026, 8);

        for (int index = 0; index < paymentRates.length; index++) {
            TeenyScoreChangeRequestDTO request =
                    teenyScorePolicyService.freeSavingMonthlyResult(
                            2L,
                            10L,
                            targetMonth,
                            paymentRates[index]);

            assertEquals(expectedScores[index], request.getAmount());
            assertEquals(
                    "SAVING_FREE_MONTHLY:10:2026-08",
                    request.getEventKey());
        }

        printResult("자유적금 월 납입률",
                "0%=0 / 1~29%=+2 / 30~59%=+4 / 60~99%=+6 / 100% 이상=+8");
    }

    @Test
    @DisplayName("자유적금은 최종 납입률 70% 이상일 때만 정상 만기로 인정한다")
    void freeSavingMaturityUsesFinalSeventyPercentRule() {
        TeenyScoreChangeRequestDTO shortfall =
                teenyScorePolicyService.freeSavingMaturity(
                        2L, 10L, 6, 69, 24);
        TeenyScoreChangeRequestDTO matured =
                teenyScorePolicyService.freeSavingMaturity(
                        2L, 11L, 6, 70, 24);

        assertEquals(-8, shortfall.getAmount());
        assertEquals(TeenyScoreEventCode.SAVING_EARLY_TERMINATED,
                shortfall.getEventCode());
        assertEquals(0, matured.getAmount());
        assertEquals(TeenyScoreEventCode.SAVING_FREE_MATURED,
                matured.getEventCode());

        printResult("자유적금 만기 경계", "최종 납입률 69%=-8점 / 70%=정상 만기 0점");
    }

    @Test
    @DisplayName("정액·자유적금은 모든 기간과 진행률 경계에서 서로 다른 감점표를 사용한다")
    void savingEarlyTerminationUsesDifferentTablesBySavingType() {
        int[][] fixedSavingScores = {
                {-3, -3, -2, -2, -1, -1, -1, -1},
                {-6, -6, -4, -4, -2, -2, -1, -1},
                {-9, -9, -6, -6, -4, -4, -2, -2},
                {-11, -11, -7, -7, -5, -5, -2, -2}
        };
        int[][] freeSavingScores = {
                {-2, -2, -2, -2, -1, -1, -1, -1},
                {-5, -5, -3, -3, -2, -2, -1, -1},
                {-8, -8, -5, -5, -3, -3, -2, -2},
                {-11, -11, -7, -7, -4, -4, -2, -2}
        };

        assertSavingEarlyTerminationTable(true, fixedSavingScores);
        assertSavingEarlyTerminationTable(false, freeSavingScores);

        printResult("적금 중도해지",
                "정액·자유적금 1·3·6·12개월의 모든 진행률 경계 검증 완료");
    }

    @Test
    @DisplayName("연속 만기는 +10점, 반복 중도해지는 -8점이다")
    void commonSavingPoliciesUseConfiguredScores() {
        assertEquals(
                10,
                teenyScorePolicyService.consecutiveMaturityBonus(
                        2L, 10L).getAmount());
        assertEquals(
                -8,
                teenyScorePolicyService.repeatedEarlyTermination(
                        2L, 10L).getAmount());

        printResult("예·적금 공통", "연속 만기=+10점 / 반복 중도해지=-8점");
    }

    @Test
    @DisplayName("대출 연체는 회차 납부율별 감점을 적용한다")
    void loanOverdueUsesPaymentRateBoundaries() {
        long[] paidAmounts = {
                0, 2_499, 2_500, 4_999, 5_000,
                7_499, 7_500, 9_999, 10_000
        };
        int[] expectedScores = {
                -8, -8, -6, -6, -4,
                -4, -2, -2, 0
        };

        for (int index = 0; index < paidAmounts.length; index++) {
            assertEquals(
                    expectedScores[index],
                    teenyScorePolicyService.loanOverdue(
                            2L,
                            20L,
                            YearMonth.of(2026, index + 1),
                            2_000,
                            8_000,
                            paidAmounts[index]).getAmount());
        }

        assertEquals(
                "LOAN_OVERDUE:20:2026-08",
                teenyScorePolicyService.loanOverdue(
                        2L, 20L, YearMonth.of(2026, 8),
                        2_000, 8_000, 7_500).getEventKey());

        printResult("대출 연체",
                "25% 미만=-8 / 25~49%=-6 / 50~74%=-4 / 75~99%=-2 / 100%=0");
    }

    @Test
    @DisplayName("대출 정상 완납은 +6점이고 최종 미상환 사건은 별도로 -20점이다")
    void loanMaturityAndDefaultUseConfiguredScores() {
        assertEquals(
                6,
                teenyScorePolicyService.loanMaturity(
                        2L, 20L).getAmount());
        assertEquals(
                -20,
                teenyScorePolicyService.loanDefault(
                        2L, 21L).getAmount());

        printResult("대출 만기", "정상 완납=+6 / 월별 미납 감점과 별도로 최종 미상환=-20");
    }

    @Test
    @DisplayName("점수 대상 퀘스트 성공은 +3점과 퀘스트 단위 멱등성 키를 만든다")
    void questCompletedAppliesThreePointsWithQuestIdentity() {
        TeenyScoreChangeRequestDTO request =
                teenyScorePolicyService.questCompleted(2L, 104L);

        assertEquals(3, request.getAmount());
        assertEquals(TeenyScoreEventCode.QUEST_COMPLETED,
                request.getEventCode());
        assertEquals("QUEST_COMPLETED:104", request.getEventKey());
        assertEquals("QUEST", request.getReferenceType());
        assertEquals(104L, request.getReferenceId());
    }

    @Test
    @DisplayName("점수 대상 퀘스트 최종 실패는 -2점과 퀘스트 단위 멱등성 키를 만든다")
    void questFailedAppliesTwoPointPenaltyWithQuestIdentity() {
        TeenyScoreChangeRequestDTO request =
                teenyScorePolicyService.questFailed(2L, 104L);

        assertEquals(-2, request.getAmount());
        assertEquals(TeenyScoreEventCode.QUEST_FAILED,
                request.getEventCode());
        assertEquals("QUEST_FAILED:104", request.getEventKey());
        assertEquals("QUEST", request.getReferenceType());
        assertEquals(104L, request.getReferenceId());
    }

    @Test
    @DisplayName("지원하지 않는 기간과 잘못된 정책 입력을 거부한다")
    void invalidPolicyInputsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> teenyScorePolicyService.depositMaturity(
                        2L, 1L, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> teenyScorePolicyService.depositEarlyTermination(
                        2L, 1L, 12, 100));
        assertThrows(
                IllegalArgumentException.class,
                () -> teenyScorePolicyService.fixedSavingInstallment(
                        2L, 1L, 0, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> teenyScorePolicyService.loanOverdue(
                        2L, 1L, YearMonth.of(2026, 8), 0, 0, 0));
        printResult("입력 검증", "기간·진행률·회차·상환액·기존 감점 오류 거부 완료");
    }

    private void printResult(String policy, String result) {
        System.out.println();
        System.out.println("[정책 테스트] " + policy);
        System.out.println("  결과: " + result);
    }

    private void assertSavingEarlyTerminationTable(
            boolean fixedSaving,
            int[][] expectedScores) {
        int[] terms = {1, 3, 6, 12};
        int[] progressBoundaries = {0, 24, 25, 49, 50, 74, 75, 99};

        for (int termIndex = 0; termIndex < terms.length; termIndex++) {
            for (int progressIndex = 0;
                    progressIndex < progressBoundaries.length;
                    progressIndex++) {
                assertEquals(
                        expectedScores[termIndex][progressIndex],
                        teenyScorePolicyService.savingEarlyTermination(
                                2L,
                                (long) termIndex + 1,
                                fixedSaving,
                                terms[termIndex],
                                progressBoundaries[progressIndex]).getAmount());
            }
        }
    }
}
