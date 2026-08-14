package com.teenyfin.teenymoney.domain.report.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.report.dto.response.AudienceResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.MoneyReportResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.PeriodResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.SpendingCategoryResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.SpendingResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WatchCategoryResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WatchSpendingResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WeeklyTrendResponseDTO;
import com.teenyfin.teenymoney.domain.report.exception.MoneyReportErrorCode;
import com.teenyfin.teenymoney.domain.report.mapper.MoneyReportMapper;
import com.teenyfin.teenymoney.domain.report.vo.ChildProfileVO;
import com.teenyfin.teenymoney.domain.report.vo.DailySpendingVO;
import com.teenyfin.teenymoney.domain.report.vo.SpendingCategoryVO;
import com.teenyfin.teenymoney.domain.report.vo.SpendingTotalVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 월간 머니 리포트 집계.
 *
 * 화면 한 장에 필요한 데이터를 한 번의 요청으로 돌려준다. 화면이 도메인 API를 여러 번
 * 불러 임의로 합산하지 않게 하는 것이 목적이다 (정의서 8.3).
 *
 * 부분 실패는 다루지 않는다. 같은 DB에 조회 몇 번이라 한 섹션만 깨지는 경우가 현실적으로
 * 없고, 커넥션이 죽으면 대개 전부 죽는다. 집계 중 예외는 그대로 올려보낸다.
 */
@Service
public class MoneyReportService {

    private static final String POLICY_WATCH = "WATCH";

    private final MoneyReportMapper moneyReportMapper;
    private final ReportPeriodCalculator periodCalculator;
    private final FamilyAccessService familyAccessService;
    private final Clock clock;

    public MoneyReportService(
            MoneyReportMapper moneyReportMapper,
            ReportPeriodCalculator periodCalculator,
            FamilyAccessService familyAccessService,
            Clock clock) {
        this.moneyReportMapper = moneyReportMapper;
        this.periodCalculator = periodCalculator;
        this.familyAccessService = familyAccessService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MoneyReportResponseDTO getMoneyReport(
            MemberPrincipal principal, Long childId, String month) {

        // childId를 요청에서 받았으므로 첫 줄에서 범위를 고정한다.
        // 자녀 본인과 그 자녀의 ACTIVE 부모만 통과하고 나머지는 403이다.
        familyAccessService.requireChildAccess(principal, childId);

        ChildProfileVO child = moneyReportMapper.selectChildProfile(childId);
        if (child == null) {
            throw new BusinessException(MoneyReportErrorCode.MONEY_REPORT_CHILD_NOT_FOUND);
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate joinedOn = child.getCreatedAt() == null
                ? null
                : child.getCreatedAt().toLocalDate();

        YearMonth targetMonth = periodCalculator.parseMonth(month, today);
        PeriodResponseDTO period = periodCalculator.calculate(targetMonth, today, joinedOn);

        SpendingTotalVO total = moneyReportMapper.selectSpendingTotal(
                childId, period.getStartDate(), period.getEndDate());
        SpendingTotalVO comparison = moneyReportMapper.selectSpendingTotal(
                childId, period.getComparisonStartDate(), period.getComparisonEndDate());

        List<SpendingCategoryVO> categoryRows = moneyReportMapper.selectSpendingByCategory(
                childId, period.getStartDate(), period.getEndDate());
        List<SpendingCategoryVO> comparisonCategoryRows =
                moneyReportMapper.selectSpendingByCategory(
                        childId,
                        period.getComparisonStartDate(),
                        period.getComparisonEndDate());

        List<DailySpendingVO> dailyRows = moneyReportMapper.selectDailySpending(
                childId, period.getStartDate(), period.getEndDate());

        List<WeeklyTrendResponseDTO> weeklyTrend = periodCalculator.weeklyTrend(
                targetMonth, period.getEndDate(), dailyRows);

        SpendingResponseDTO spending = new SpendingResponseDTO(
                total.getTotalAmount(),
                total.getPaymentCount(),
                comparison.getTotalAmount(),
                comparison.getPaymentCount(),
                total.getTotalAmount() - comparison.getTotalAmount(),
                total.getPaymentCount() - comparison.getPaymentCount(),
                weeklyTrend,
                toCategories(categoryRows, total.getTotalAmount()));

        WatchSpendingResponseDTO watchSpending = toWatchSpending(
                categoryRows, comparisonCategoryRows, total.getPaymentCount());

        return new MoneyReportResponseDTO(
                period,
                new AudienceResponseDTO(
                        periodCalculator.ageBand(child.getBirthDate(), today)),
                periodCalculator.availableMonths(today, joinedOn),
                spending,
                watchSpending);
    }

    /**
     * 카테고리 × 정책으로 나뉜 행을 카테고리 단위로 합친다.
     *
     * 정책이 바뀌어 같은 카테고리의 과거 결제가 ALLOW와 WATCH 양쪽에 걸려 있으면
     * 쿼리 결과가 두 줄로 나온다. 화면의 카테고리 목록에서는 한 줄이어야 한다.
     *
     * 정렬은 정의서 6.4 순서다. 합치고 나면 순서가 달라질 수 있어 여기서 다시 정렬한다.
     */
    private List<SpendingCategoryResponseDTO> toCategories(
            List<SpendingCategoryVO> rows, long totalAmount) {

        Map<Long, long[]> merged = new LinkedHashMap<>();   // categoryId -> {amount, count}
        Map<Long, String> names = new LinkedHashMap<>();

        for (SpendingCategoryVO row : rows) {
            long[] acc = merged.computeIfAbsent(row.getCategoryId(), k -> new long[2]);
            acc[0] += row.getAmount();
            acc[1] += row.getPaymentCount();
            names.putIfAbsent(row.getCategoryId(), row.getCategoryName());
        }

        List<SpendingCategoryResponseDTO> categories = new ArrayList<>();
        for (Map.Entry<Long, long[]> entry : merged.entrySet()) {
            long amount = entry.getValue()[0];
            categories.add(new SpendingCategoryResponseDTO(
                    entry.getKey(),
                    names.get(entry.getKey()),
                    amount,
                    (int) entry.getValue()[1],
                    ratio(amount, totalAmount)));
        }

        categories.sort(
                Comparator.comparingLong(SpendingCategoryResponseDTO::getAmount).reversed()
                        .thenComparing(Comparator.comparingInt(
                                SpendingCategoryResponseDTO::getPaymentCount).reversed())
                        .thenComparing(SpendingCategoryResponseDTO::getCategoryName));
        return categories;
    }

    /** 결제가 하나도 없는 달에 0으로 나누지 않는다. */
    private int ratio(long amount, long totalAmount) {
        if (totalAmount <= 0) {
            return 0;
        }
        return Math.round((float) amount * 100 / totalAmount);
    }

    private WatchSpendingResponseDTO toWatchSpending(
            List<SpendingCategoryVO> rows,
            List<SpendingCategoryVO> comparisonRows,
            int totalPaymentCount) {

        List<WatchCategoryResponseDTO> categories = new ArrayList<>();
        long amount = 0;
        int count = 0;

        for (SpendingCategoryVO row : rows) {
            if (!POLICY_WATCH.equals(row.getAppliedPolicy())) {
                continue;
            }
            amount += row.getAmount();
            count += row.getPaymentCount();
            categories.add(new WatchCategoryResponseDTO(
                    row.getCategoryId(),
                    row.getCategoryName(),
                    row.getPaymentCount(),
                    row.getAmount()));
        }

        int comparisonCount = 0;
        for (SpendingCategoryVO row : comparisonRows) {
            if (POLICY_WATCH.equals(row.getAppliedPolicy())) {
                comparisonCount += row.getPaymentCount();
            }
        }

        categories.sort(
                Comparator.comparingLong(WatchCategoryResponseDTO::getAmount).reversed()
                        .thenComparing(WatchCategoryResponseDTO::getCategoryName));

        return new WatchSpendingResponseDTO(
                count, amount, totalPaymentCount, comparisonCount, categories);
    }
}
