package com.teenyfin.teenymoney.domain.report.service;

import com.teenyfin.teenymoney.domain.report.dto.response.AvailableMonthResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.PeriodResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WeeklyTrendResponseDTO;
import com.teenyfin.teenymoney.domain.report.exception.MoneyReportErrorCode;
import com.teenyfin.teenymoney.domain.report.vo.DailySpendingVO;
import com.teenyfin.teenymoney.domain.report.vo.ProductPeriodVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB도 스프링 컨텍스트도 없는 순수 계산 테스트.
 *
 * 날짜를 전부 고정값으로 넣기 때문에 언제 돌려도 결과가 같다. 주차 규칙을 SQL이 아니라
 * Java에 둔 이유가 이것이다.
 */
class ReportPeriodCalculatorTest {

    private final ReportPeriodCalculator calculator = new ReportPeriodCalculator();

    private static final LocalDate JOINED_2026_01 = LocalDate.of(2026, 1, 15);

    @Nested
    @DisplayName("month 파싱")
    class ParseMonth {

        @Test
        @DisplayName("비어 있으면 오늘이 속한 달")
        void blankMeansCurrentMonth() {
            LocalDate today = LocalDate.of(2026, 8, 13);

            assertThat(calculator.parseMonth(null, today)).isEqualTo(YearMonth.of(2026, 8));
            assertThat(calculator.parseMonth("", today)).isEqualTo(YearMonth.of(2026, 8));
            assertThat(calculator.parseMonth("  ", today)).isEqualTo(YearMonth.of(2026, 8));
        }

        @Test
        @DisplayName("yyyy-MM 이 아니면 400")
        void rejectsBadFormat() {
            LocalDate today = LocalDate.of(2026, 8, 13);

            for (String bad : List.of("2026/08", "26-08", "2026-8-1", "August", "2026-13")) {
                assertThatThrownBy(() -> calculator.parseMonth(bad, today))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue(
                                "errorCode", MoneyReportErrorCode.MONEY_REPORT_INVALID_MONTH);
            }
        }
    }

    @Nested
    @DisplayName("조회 기간과 비교 기간")
    class Period {

        @Test
        @DisplayName("진행 중인 달은 1일부터 오늘까지, 비교 기간은 전월 같은 일수")
        void inProgressMonth() {
            PeriodResponseDTO period = calculator.calculate(
                    YearMonth.of(2026, 8), LocalDate.of(2026, 8, 13), JOINED_2026_01);

            assertThat(period.getYearMonth()).isEqualTo("2026-08");
            assertThat(period.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(period.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 13));
            assertThat(period.getStatus()).isEqualTo("IN_PROGRESS");
            assertThat(period.getComparisonStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(period.getComparisonEndDate()).isEqualTo(LocalDate.of(2026, 7, 13));
        }

        @Test
        @DisplayName("완료된 달은 1일부터 말일까지, 비교 기간은 직전 달 전체")
        void completedMonth() {
            PeriodResponseDTO period = calculator.calculate(
                    YearMonth.of(2026, 7), LocalDate.of(2026, 8, 13), JOINED_2026_01);

            assertThat(period.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(period.getEndDate()).isEqualTo(LocalDate.of(2026, 7, 31));
            assertThat(period.getStatus()).isEqualTo("COMPLETED");
            assertThat(period.getComparisonStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(period.getComparisonEndDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        }

        @Test
        @DisplayName("전월에 대응 일자가 없으면 전월 말일로 자른다")
        void clampsComparisonToShorterMonth() {
            // 2026-02는 28일까지 (평년)
            PeriodResponseDTO period = calculator.calculate(
                    YearMonth.of(2026, 3), LocalDate.of(2026, 3, 31), JOINED_2026_01);

            assertThat(period.getEndDate()).isEqualTo(LocalDate.of(2026, 3, 31));
            assertThat(period.getComparisonEndDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        @DisplayName("윤년 2월도 실제 말일까지만")
        void clampsToLeapFebruary() {
            // 2024-02는 29일까지
            PeriodResponseDTO period = calculator.calculate(
                    YearMonth.of(2024, 3), LocalDate.of(2024, 3, 31), LocalDate.of(2023, 1, 1));

            assertThat(period.getComparisonEndDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        }

        @Test
        @DisplayName("미래 월은 400")
        void rejectsFutureMonth() {
            assertThatThrownBy(() -> calculator.calculate(
                    YearMonth.of(2026, 9), LocalDate.of(2026, 8, 13), JOINED_2026_01))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue(
                            "errorCode", MoneyReportErrorCode.MONEY_REPORT_FUTURE_MONTH);
        }

        @Test
        @DisplayName("가입 이전 월은 400, 가입한 달 자체는 통과")
        void rejectsMonthBeforeJoin() {
            LocalDate today = LocalDate.of(2026, 8, 13);

            assertThatThrownBy(() -> calculator.calculate(
                    YearMonth.of(2025, 12), today, JOINED_2026_01))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue(
                            "errorCode", MoneyReportErrorCode.MONEY_REPORT_MONTH_BEFORE_JOIN);

            // 가입일이 1월 15일이어도 1월은 조회할 수 있어야 한다
            assertThat(calculator.calculate(YearMonth.of(2026, 1), today, JOINED_2026_01)
                    .getStatus()).isEqualTo("COMPLETED");
        }
    }

    @Nested
    @DisplayName("월 목록")
    class AvailableMonths {

        @Test
        @DisplayName("가입 월부터 현재 월까지 최신 순, 미래 월은 없다")
        void listsFromJoinToNow() {
            List<AvailableMonthResponseDTO> months = calculator.availableMonths(
                    LocalDate.of(2026, 8, 13), LocalDate.of(2026, 5, 20),
                    Set.of("2026-07", "2026-06", "2026-05"));

            assertThat(months).extracting(AvailableMonthResponseDTO::getYearMonth)
                    .containsExactly("2026-08", "2026-07", "2026-06", "2026-05");
            assertThat(months).extracting(AvailableMonthResponseDTO::getStatus)
                    .containsExactly("IN_PROGRESS", "COMPLETED", "COMPLETED", "COMPLETED");
        }

        @Test
        @DisplayName("이번 달에 가입했으면 한 달만")
        void joinedThisMonth() {
            List<AvailableMonthResponseDTO> months = calculator.availableMonths(
                    LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 2), Set.of());

            assertThat(months).hasSize(1);
            assertThat(months.get(0).getYearMonth()).isEqualTo("2026-08");
            assertThat(months.get(0).getStatus()).isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("활동이 없었던 지난 달은 기록 없음")
        void monthWithoutActivityIsNoRecord() {
            List<AvailableMonthResponseDTO> months = calculator.availableMonths(
                    LocalDate.of(2026, 8, 13), LocalDate.of(2026, 5, 20),
                    Set.of("2026-07", "2026-05"));   // 6월만 활동 없음

            assertThat(months).extracting(AvailableMonthResponseDTO::getStatus)
                    .containsExactly("IN_PROGRESS", "COMPLETED", "NO_RECORD", "COMPLETED");
        }

        @Test
        @DisplayName("현재 월은 활동이 없어도 진행 중이다")
        void currentMonthIsAlwaysInProgress() {
            List<AvailableMonthResponseDTO> months = calculator.availableMonths(
                    LocalDate.of(2026, 8, 13), LocalDate.of(2026, 7, 1), Set.of());

            assertThat(months).extracting(AvailableMonthResponseDTO::getStatus)
                    .containsExactly("IN_PROGRESS", "NO_RECORD");
        }
    }

    @Nested
    @DisplayName("주차 버킷")
    class Weekly {

        @Test
        @DisplayName("2026-07은 1일이 수요일이라 5주")
        void july2026HasFiveWeeks() {
            List<WeeklyTrendResponseDTO> weeks = calculator.weeklyTrend(
                    YearMonth.of(2026, 7), LocalDate.of(2026, 7, 31), List.of());

            assertThat(weeks).hasSize(5);
            assertThat(boundaries(weeks)).containsExactly(
                    "07-01~07-05", "07-06~07-12", "07-13~07-19", "07-20~07-26", "07-27~07-31");
        }

        @Test
        @DisplayName("2026-08은 1일이 토요일이라 6주, 마지막 주는 31일 하루")
        void august2026HasSixWeeks() {
            List<WeeklyTrendResponseDTO> weeks = calculator.weeklyTrend(
                    YearMonth.of(2026, 8), LocalDate.of(2026, 8, 31), List.of());

            assertThat(weeks).hasSize(6);
            assertThat(boundaries(weeks)).containsExactly(
                    "08-01~08-02", "08-03~08-09", "08-10~08-16",
                    "08-17~08-23", "08-24~08-30", "08-31~08-31");
        }

        @Test
        @DisplayName("1일이 일요일이면 1주차가 하루뿐")
        void firstWeekCanBeSingleDay() {
            // 2026-02-01은 일요일
            List<WeeklyTrendResponseDTO> weeks = calculator.weeklyTrend(
                    YearMonth.of(2026, 2), LocalDate.of(2026, 2, 28), List.of());

            assertThat(weeks.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(weeks.get(0).getEndDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        }

        @Test
        @DisplayName("일자별 합계가 해당 주차에 더해진다")
        void sumsDailyRowsIntoWeeks() {
            List<DailySpendingVO> daily = List.of(
                    daily(LocalDate.of(2026, 8, 2), 4200, 1),    // 1주
                    daily(LocalDate.of(2026, 8, 3), 5000, 1),    // 2주
                    daily(LocalDate.of(2026, 8, 9), 600, 2),     // 2주
                    daily(LocalDate.of(2026, 8, 12), 12800, 3)); // 3주

            List<WeeklyTrendResponseDTO> weeks = calculator.weeklyTrend(
                    YearMonth.of(2026, 8), LocalDate.of(2026, 8, 13), daily);

            assertThat(weeks.get(0).getAmount()).isEqualTo(4200L);
            assertThat(weeks.get(0).getPaymentCount()).isEqualTo(1);
            assertThat(weeks.get(1).getAmount()).isEqualTo(5600L);
            assertThat(weeks.get(1).getPaymentCount()).isEqualTo(3);
            assertThat(weeks.get(2).getAmount()).isEqualTo(12800L);
        }

        @Test
        @DisplayName("아직 오지 않은 주차는 null, 지난 주차의 0원은 0")
        void futureWeeksAreNullNotZero() {
            // 조회 종료일 8/13은 3주(10~16) 안에 있다. 4~6주는 아직 오지 않았다.
            List<WeeklyTrendResponseDTO> weeks = calculator.weeklyTrend(
                    YearMonth.of(2026, 8), LocalDate.of(2026, 8, 13), List.of());

            assertThat(weeks.get(0).getAmount()).isZero();   // 지났는데 안 씀
            assertThat(weeks.get(1).getAmount()).isZero();
            assertThat(weeks.get(2).getAmount()).isZero();   // 오늘이 낀 주
            assertThat(weeks.get(3).getAmount()).isNull();   // 아직 안 옴
            assertThat(weeks.get(4).getAmount()).isNull();
            assertThat(weeks.get(5).getAmount()).isNull();
            assertThat(weeks.get(3).getPaymentCount()).isNull();
        }

        @Test
        @DisplayName("완료된 달에는 null 주차가 없다")
        void completedMonthHasNoNulls() {
            List<WeeklyTrendResponseDTO> weeks = calculator.weeklyTrend(
                    YearMonth.of(2026, 7), LocalDate.of(2026, 7, 31), List.of());

            assertThat(weeks).extracting(WeeklyTrendResponseDTO::getAmount)
                    .doesNotContainNull();
        }

        private List<String> boundaries(List<WeeklyTrendResponseDTO> weeks) {
            return weeks.stream()
                    .map(w -> String.format("%02d-%02d~%02d-%02d",
                            w.getStartDate().getMonthValue(), w.getStartDate().getDayOfMonth(),
                            w.getEndDate().getMonthValue(), w.getEndDate().getDayOfMonth()))
                    .toList();
        }

        private DailySpendingVO daily(LocalDate date, long amount, int count) {
            return DailySpendingVO.builder()
                    .spentDate(date).amount(amount).paymentCount(count).build();
        }
    }

    @Nested
    @DisplayName("상품이 살아 있던 달")
    class ProductMonths {

        @Test
        @DisplayName("시작월부터 종료월까지 펼친다")
        void expandsClosedRange() {
            var months = calculator.monthsCoveredByProducts(
                    List.of(ProductPeriodVO.builder()
                            .startDate(LocalDate.of(2026, 5, 20))
                            .endDate(LocalDate.of(2026, 7, 3)).build()),
                    LocalDate.of(2026, 8, 13));

            assertThat(months).containsExactlyInAnyOrder("2026-05", "2026-06", "2026-07");
        }

        @Test
        @DisplayName("끝이 열려 있으면 오늘까지")
        void expandsOpenRangeToToday() {
            var months = calculator.monthsCoveredByProducts(
                    List.of(ProductPeriodVO.builder()
                            .startDate(LocalDate.of(2026, 6, 5)).endDate(null).build()),
                    LocalDate.of(2026, 8, 13));

            assertThat(months).containsExactlyInAnyOrder("2026-06", "2026-07", "2026-08");
        }

        @Test
        @DisplayName("만기가 미래여도 오늘을 넘지 않는다")
        void doesNotGoBeyondToday() {
            var months = calculator.monthsCoveredByProducts(
                    List.of(ProductPeriodVO.builder()
                            .startDate(LocalDate.of(2026, 7, 3))
                            .endDate(LocalDate.of(2026, 12, 1)).build()),
                    LocalDate.of(2026, 8, 13));

            assertThat(months).containsExactlyInAnyOrder("2026-07", "2026-08");
        }

        @Test
        @DisplayName("시작일이 없는 상품은 무시한다")
        void skipsUnstartedProducts() {
            assertThat(calculator.monthsCoveredByProducts(
                    List.of(ProductPeriodVO.builder().startDate(null).build()),
                    LocalDate.of(2026, 8, 13))).isEmpty();
        }
    }

    @Nested
    @DisplayName("연령 모드")
    class AgeBand {

        @Test
        @DisplayName("만 13세부터 TEEN")
        void teenFromThirteen() {
            LocalDate today = LocalDate.of(2026, 8, 13);

            // 생일 당일에 만 13세가 된다
            assertThat(calculator.ageBand(LocalDate.of(2013, 8, 13), today)).isEqualTo("TEEN");
            // 하루 뒤가 생일이면 아직 만 12세
            assertThat(calculator.ageBand(LocalDate.of(2013, 8, 14), today)).isEqualTo("JUNIOR");
            assertThat(calculator.ageBand(LocalDate.of(2016, 1, 1), today)).isEqualTo("JUNIOR");
        }

        @Test
        @DisplayName("생년월일이 없으면 쉬운 쪽인 JUNIOR")
        void nullBirthDateFallsBackToJunior() {
            assertThat(calculator.ageBand(null, LocalDate.of(2026, 8, 13))).isEqualTo("JUNIOR");
        }
    }
}
