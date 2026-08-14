package com.teenyfin.teenymoney.domain.report.service;

import com.teenyfin.teenymoney.domain.report.dto.response.AvailableMonthResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.PeriodResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WeeklyTrendResponseDTO;
import com.teenyfin.teenymoney.domain.report.exception.MoneyReportErrorCode;
import com.teenyfin.teenymoney.domain.report.vo.DailySpendingVO;
import com.teenyfin.teenymoney.domain.report.vo.ProductPeriodVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 리포트의 날짜 계산만 담당한다. DB도 인증도 보지 않는다.
 *
 * 서비스에서 떼어낸 이유는 이것이 리포트에서 가장 틀리기 쉬운 부분이기 때문이다.
 * 전월에 같은 일자가 없는 경우, 첫 주와 마지막 주의 절단, 아직 오지 않은 주차,
 * 가입 이전 월. 전부 DB 없이 단위 테스트로 덮을 수 있는 자리에 둔다.
 *
 * 오늘 날짜는 인자로 받는다. 안에서 LocalDate.now()를 부르면 테스트가 실행 시점에
 * 묶여버린다.
 */
@Component
public class ReportPeriodCalculator {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_NO_RECORD = "NO_RECORD";

    public static final String AGE_BAND_JUNIOR = "JUNIOR";
    public static final String AGE_BAND_TEEN = "TEEN";

    /** 정의서 3: JUNIOR 만 7~12세, TEEN 만 13~18세 */
    private static final int TEEN_FROM_AGE = 13;

    private static final DateTimeFormatter YEAR_MONTH =
            DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * month 파라미터를 파싱한다. 비어 있으면 오늘이 속한 달이다.
     *
     * @throws BusinessException 형식이 yyyy-MM 이 아니면 MONEY_REPORT_INVALID_MONTH
     */
    public YearMonth parseMonth(String month, LocalDate today) {
        if (month == null || month.isBlank()) {
            return YearMonth.from(today);
        }
        try {
            return YearMonth.parse(month.trim(), YEAR_MONTH);
        } catch (DateTimeParseException e) {
            throw new BusinessException(MoneyReportErrorCode.MONEY_REPORT_INVALID_MONTH);
        }
    }

    /**
     * 조회 기간과 비교 기간을 계산한다.
     *
     * 진행 중인 달은 1일부터 오늘까지이고 비교 기간은 전월의 같은 일수다.
     * 완료된 달은 1일부터 말일까지이고 비교 기간은 직전 달 전체다.
     *
     * @throws BusinessException 미래 월이면 MONEY_REPORT_FUTURE_MONTH,
     *                           가입 이전 월이면 MONEY_REPORT_MONTH_BEFORE_JOIN
     */
    public PeriodResponseDTO calculate(YearMonth month, LocalDate today, LocalDate joinedOn) {
        YearMonth currentMonth = YearMonth.from(today);

        if (month.isAfter(currentMonth)) {
            throw new BusinessException(MoneyReportErrorCode.MONEY_REPORT_FUTURE_MONTH);
        }
        if (joinedOn != null && month.isBefore(YearMonth.from(joinedOn))) {
            throw new BusinessException(MoneyReportErrorCode.MONEY_REPORT_MONTH_BEFORE_JOIN);
        }

        boolean inProgress = month.equals(currentMonth);
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = inProgress ? today : month.atEndOfMonth();

        YearMonth previousMonth = month.minusMonths(1);
        LocalDate comparisonStart = previousMonth.atDay(1);

        // 진행 중이면 전월의 같은 일자까지. 전월에 그 일자가 없으면 전월 말일로 자른다.
        // (3월 31일 조회 -> 2월 28일). 완료된 달이면 직전 달 전체다.
        LocalDate comparisonEnd = inProgress
                ? previousMonth.atDay(Math.min(endDate.getDayOfMonth(),
                                               previousMonth.lengthOfMonth()))
                : previousMonth.atEndOfMonth();

        return new PeriodResponseDTO(
                month.format(YEAR_MONTH),
                startDate,
                endDate,
                inProgress ? STATUS_IN_PROGRESS : STATUS_COMPLETED,
                comparisonStart,
                comparisonEnd);
    }

    /**
     * 월 선택에 노출할 월 목록. 가입 월부터 현재 월까지, 최신 월이 먼저다.
     * 미래 월은 노출하지 않는다 (정의서 4.4).
     *
     * 현재 월은 늘 진행 중이다. 지난 달은 활동이 있었으면 완료, 없었으면 기록 없음이다.
     * 활동 판정은 결제만이 아니라 리포트가 보여주는 모든 도메인을 훑은 결과여야 한다.
     * 결제만 보면 저축이나 퀘스트만 한 달이 '기록 없음'으로 잘못 나온다.
     *
     * @param activeMonths 활동이 하나라도 있었던 달의 yyyy-MM 집합
     */
    public List<AvailableMonthResponseDTO> availableMonths(
            LocalDate today, LocalDate joinedOn, Set<String> activeMonths) {

        YearMonth currentMonth = YearMonth.from(today);
        YearMonth from = joinedOn == null ? currentMonth : YearMonth.from(joinedOn);
        if (from.isAfter(currentMonth)) {
            from = currentMonth;
        }

        List<AvailableMonthResponseDTO> months = new ArrayList<>();
        for (YearMonth m = currentMonth; !m.isBefore(from); m = m.minusMonths(1)) {
            String label = m.format(YEAR_MONTH);
            String status;
            if (m.equals(currentMonth)) {
                status = STATUS_IN_PROGRESS;
            } else if (activeMonths.contains(label)) {
                status = STATUS_COMPLETED;
            } else {
                status = STATUS_NO_RECORD;
            }
            months.add(new AvailableMonthResponseDTO(label, status));
        }
        return months;
    }

    /**
     * 일자별 합계를 주차로 묶는다.
     *
     * 주차는 월요일에 시작해 일요일에 끝나고, 그 달의 1일과 말일에서만 잘린다.
     * 2026-07(1일=수)은 1~5 / 6~12 / 13~19 / 20~26 / 27~31 로 5주,
     * 2026-08(1일=토)은 1~2 / 3~9 / 10~16 / 17~23 / 24~30 / 31 로 6주다.
     *
     * 진행 중인 달도 그 달의 모든 주차를 돌려주되, 조회 종료일보다 뒤에 있는 주차는
     * 금액과 건수가 null 이다. 축을 고정하면서(매일 열어도 막대 개수가 안 바뀐다)
     * 아직 오지 않은 주를 "0원 썼다"고 말하지 않기 위해서다.
     */
    public List<WeeklyTrendResponseDTO> weeklyTrend(
            YearMonth month, LocalDate periodEnd, List<DailySpendingVO> dailyRows) {

        Map<LocalDate, DailySpendingVO> byDate = new HashMap<>();
        for (DailySpendingVO row : dailyRows) {
            byDate.put(row.getSpentDate(), row);
        }

        LocalDate monthEnd = month.atEndOfMonth();
        List<WeeklyTrendResponseDTO> weeks = new ArrayList<>();

        LocalDate weekStart = month.atDay(1);
        int weekNo = 1;
        while (!weekStart.isAfter(monthEnd)) {
            LocalDate weekEnd = lastDayOfWeekWithin(weekStart, monthEnd);

            if (weekStart.isAfter(periodEnd)) {
                // 아직 오지 않은 주차. 0원과 구분해야 하므로 null 로 둔다.
                weeks.add(new WeeklyTrendResponseDTO(weekNo, weekStart, weekEnd, null, null));
            } else {
                long amount = 0;
                int count = 0;
                for (LocalDate d = weekStart; !d.isAfter(weekEnd); d = d.plusDays(1)) {
                    DailySpendingVO row = byDate.get(d);
                    if (row != null) {
                        amount += row.getAmount();
                        count += row.getPaymentCount();
                    }
                }
                weeks.add(new WeeklyTrendResponseDTO(weekNo, weekStart, weekEnd, amount, count));
            }

            weekStart = weekEnd.plusDays(1);
            weekNo++;
        }
        return weeks;
    }

    /**
     * 가입 상품이 살아 있던 달을 yyyy-MM 으로 펼친다.
     *
     * 활동 월 판정에 쓴다. 결제도 퀘스트도 없던 달이라도 그 달에 적금이 굴러가고 있었다면
     * 리포트에 진행 카드가 뜨므로 '기록 없음'이 아니다.
     *
     * 끝이 열려 있는(아직 진행 중인) 상품은 오늘까지로 본다.
     */
    public Set<String> monthsCoveredByProducts(
            List<ProductPeriodVO> periods, LocalDate today) {

        Set<String> months = new HashSet<>();
        for (ProductPeriodVO period : periods) {
            if (period.getStartDate() == null) {
                continue;
            }
            YearMonth from = YearMonth.from(period.getStartDate());
            YearMonth to = YearMonth.from(
                    period.getEndDate() == null ? today : period.getEndDate());
            if (to.isAfter(YearMonth.from(today))) {
                to = YearMonth.from(today);
            }
            for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
                months.add(m.format(YEAR_MONTH));
            }
        }
        return months;
    }

    /** 그 주의 일요일. 달을 넘어가면 말일에서 자른다. */
    private LocalDate lastDayOfWeekWithin(LocalDate weekStart, LocalDate monthEnd) {
        int untilSunday = DayOfWeek.SUNDAY.getValue() - weekStart.getDayOfWeek().getValue();
        LocalDate sunday = weekStart.plusDays(untilSunday);
        return sunday.isAfter(monthEnd) ? monthEnd : sunday;
    }

    /**
     * 만 나이로 연령 모드를 정한다 (정의서 3).
     *
     * 과거 달 리포트를 보더라도 현재 연령을 적용하므로 조회 월이 아니라 오늘을 기준으로 한다.
     * 생년월일이 없으면 쉬운 쪽인 JUNIOR 로 둔다.
     */
    public String ageBand(LocalDate birthDate, LocalDate today) {
        if (birthDate == null) {
            return AGE_BAND_JUNIOR;
        }
        long age = ChronoUnit.YEARS.between(birthDate, today);
        return age >= TEEN_FROM_AGE ? AGE_BAND_TEEN : AGE_BAND_JUNIOR;
    }
}
