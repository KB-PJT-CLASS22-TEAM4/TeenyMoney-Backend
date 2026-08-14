package com.teenyfin.teenymoney.domain.report.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.report.dto.response.AudienceResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.InsightResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.MoneyReportResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.MonthlyScoreReasonResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.MonthlyScoreResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.PeriodResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.SummaryResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.SpendingCategoryResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.SpendingResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WatchCategoryResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WatchSpendingResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WeeklyTrendResponseDTO;
import com.teenyfin.teenymoney.domain.report.exception.MoneyReportErrorCode;
import com.teenyfin.teenymoney.domain.report.mapper.MoneyReportMapper;
import com.teenyfin.teenymoney.domain.report.vo.ChildProfileVO;
import com.teenyfin.teenymoney.domain.report.vo.ClosingProductVO;
import com.teenyfin.teenymoney.domain.report.vo.DailySpendingVO;
import com.teenyfin.teenymoney.domain.report.vo.LoanOverdueVO;
import com.teenyfin.teenymoney.domain.report.vo.LoanRepaymentVO;
import com.teenyfin.teenymoney.domain.report.vo.MoneyFlowVO;
import com.teenyfin.teenymoney.domain.report.vo.PermissionSummaryVO;
import com.teenyfin.teenymoney.domain.report.vo.QuestSummaryVO;
import com.teenyfin.teenymoney.domain.report.vo.ScoreHistoryVO;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** 정의서 6.6: 대표 원인은 최대 2개 */
    private static final int TOP_REASON_LIMIT = 2;

    /** 저축·상환 카드는 특정 가입 건이 아니라 그 달의 흐름이라 목록 화면으로 보낸다. */
    private static final String PRODUCTS_LINK = "/child/finance/my-products";

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

        MoneyFlowVO flow = moneyReportMapper.selectMoneyFlow(
                childId, period.getStartDate(), period.getEndDate());
        LoanRepaymentVO repayment = moneyReportMapper.selectLoanRepayment(
                childId, period.getStartDate(), period.getEndDate());
        PermissionSummaryVO permissions = moneyReportMapper.selectPermissionSummary(
                childId, period.getStartDate(), period.getEndDate());
        QuestSummaryVO quests = moneyReportMapper.selectQuestSummary(
                childId, period.getStartDate(), period.getEndDate());
        List<ScoreHistoryVO> scoreRows = moneyReportMapper.selectScoreHistory(
                childId, period.getStartDate(), period.getEndDate());

        // 만기는 조회 종료일(오늘)이 아니라 그 달 말일까지 본다.
        // 진행 중인 달이라면 "이번 달 안에 만기가 온다"가 지금 알아야 할 정보다.
        List<ClosingProductVO> closingProducts = moneyReportMapper.selectClosingProducts(
                childId, period.getStartDate(), targetMonth.atEndOfMonth());

        // 연체는 진행 중인 달에만 본다. 과거 달에는 조회조차 하지 않는다.
        LoanOverdueVO overdue = ReportPeriodCalculator.STATUS_IN_PROGRESS.equals(period.getStatus())
                ? moneyReportMapper.selectLoanOverdue(childId)
                : null;

        return new MoneyReportResponseDTO(
                period,
                new AudienceResponseDTO(
                        periodCalculator.ageBand(child.getBirthDate(), today)),
                periodCalculator.availableMonths(today, joinedOn,
                        activeMonths(childId, today)),
                toSummary(total, flow, repayment),
                buildInsights(spending, permissions, quests, flow, repayment, overdue, closingProducts),
                spending,
                watchSpending,
                toMonthlyScore(scoreRows));
    }

    /**
     * '기록 없음'을 붙이지 않을 달.
     *
     * 실제로 무언가 일어난 달과, 가입 상품이 굴러가고 있던 달을 합친다.
     * 상품 기간을 빼먹으면 결제도 퀘스트도 없던 달이 '기록 없음'으로 표시되는데
     * 정작 열어보면 적금 진행 카드가 떠 있다.
     */
    private Set<String> activeMonths(Long childId, LocalDate today) {
        Set<String> months = new HashSet<>(moneyReportMapper.selectActivityMonths(childId));
        months.addAll(periodCalculator.monthsCoveredByProducts(
                moneyReportMapper.selectProductPeriods(childId), today));
        return months;
    }

    private SummaryResponseDTO toSummary(
            SpendingTotalVO total, MoneyFlowVO flow, LoanRepaymentVO repayment) {

        return new SummaryResponseDTO(
                total.getTotalAmount(),
                total.getPaymentCount(),
                flow.getDepositAmount() + flow.getSavingAmount(),
                flow.getDepositAmount(),
                flow.getSavingAmount(),
                flow.getEarnedAmount(),
                flow.getQuestRewardCount(),
                repayment.getPaidPrincipal() + repayment.getPaidInterest(),
                repayment.getPaidPrincipal(),
                repayment.getPaidInterest(),
                repayment.getRepaidCount());
    }

    /**
     * 금융 습관 카드를 만든다.
     *
     * 서버는 코드와 수치만 넣는다. 문장은 화면의 연령별 문구 사전이 조립한다 (정의서 7.2).
     * 보여줄 사실이 없는 카드는 아예 넣지 않는다. "결제 0건"을 굳이 카드로 만들지 않는다는 뜻이고,
     * 이것이 정의서 6.3의 "데이터로 증명할 수 있는 행동만 보여준다"에 해당한다.
     *
     * 순서는 디자인 화면 순서다: 소비 -> 소통 -> 저축·상환 -> 책임.
     */
    private List<InsightResponseDTO> buildInsights(
            SpendingResponseDTO spending,
            PermissionSummaryVO permissions,
            QuestSummaryVO quests,
            MoneyFlowVO flow,
            LoanRepaymentVO repayment,
            LoanOverdueVO overdue,
            List<ClosingProductVO> closingProducts) {

        List<InsightResponseDTO> insights = new ArrayList<>();

        if (spending.getPaymentCount() > 0 && !spending.getCategories().isEmpty()) {
            SpendingCategoryResponseDTO top = spending.getCategories().get(0);
            insights.add(new InsightResponseDTO(
                    "SPENDING_TOP_CATEGORY",
                    metrics(
                            "totalAmount", spending.getTotalAmount(),
                            "paymentCount", spending.getPaymentCount(),
                            "topCategoryName", top.getCategoryName(),
                            "topCategoryAmount", top.getAmount(),
                            "topCategoryCount", top.getPaymentCount(),
                            "topCategoryRatio", top.getRatio(),
                            "comparisonAmountDelta", spending.getComparisonAmountDelta(),
                            "comparisonCountDelta", spending.getComparisonCountDelta()),
                    "/child/transactions"));
        }

        if (permissions.getRequestCount() > 0) {
            insights.add(new InsightResponseDTO(
                    "PERMISSION_STATUS_SUMMARY",
                    metrics(
                            "requestCount", permissions.getRequestCount(),
                            "approvedCount", permissions.getApprovedCount(),
                            "rejectedCount", permissions.getRejectedCount(),
                            "pendingCount", permissions.getPendingCount(),
                            "expiredCount", permissions.getExpiredCount(),
                            "reasonWrittenCount", permissions.getReasonWrittenCount()),
                    "/child/today-permissions"));
        }

        // 저축과 상환은 '그 달에 얼마를 넣고 얼마를 갚았나'로 본다.
        //
        // 처음에는 가입 상품 하나를 골라 진행률(3/6회, 50%)을 보여줬는데 두 가지가 걸렸다.
        // 상품이 여러 개면 하나만 고를 수밖에 없어 나머지 저축이 리포트에서 통째로 사라졌고,
        // 진행률은 그 달의 사건이 아니라 늘 떠 있는 상태값이라 이 화면의 성격과도 달랐다.
        // 흐름으로 바꾸니 상품을 고를 필요가 없어져 두 문제가 함께 사라진다.
        // 진행률·다음 납입일·남은 원금은 금융상품 화면이 답한다.
        long savedAmount = flow.getDepositAmount() + flow.getSavingAmount();
        if (savedAmount > 0) {
            insights.add(new InsightResponseDTO(
                    "SAVING_PAYMENT",
                    metrics(
                            "amount", savedAmount,
                            "savingAmount", flow.getSavingAmount(),
                            "savingProductCount", flow.getSavingProductCount(),
                            "savingPaymentCount", flow.getSavingPaymentCount(),
                            "depositAmount", flow.getDepositAmount(),
                            "depositProductCount", flow.getDepositProductCount(),
                            "depositPaymentCount", flow.getDepositPaymentCount()),
                    PRODUCTS_LINK));
        }

        long repaidAmount = repayment.getPaidPrincipal() + repayment.getPaidInterest();
        if (repaidAmount > 0) {
            insights.add(new InsightResponseDTO(
                    "LOAN_REPAYMENT",
                    metrics(
                            "amount", repaidAmount,
                            "principal", repayment.getPaidPrincipal(),
                            "interest", repayment.getPaidInterest(),
                            "repaidCount", repayment.getRepaidCount()),
                    PRODUCTS_LINK));
        }

        // 연체만 흐름 원칙의 예외다. 그 달의 사건이 아니라 지금 확인해야 할 상태이고,
        // 늦게 알수록 손해라 리포트에서 한 번 더 알린다 (정의서 6.3.C).
        //
        // 대신 진행 중인 달에만 붙인다. 과거 달 리포트에 오늘의 연체를 띄우면
        // "7월에 연체했다"로 읽히고, 실제로 그렇게 새는 버그를 한 번 냈다.
        // 호출부가 과거 달이면 아예 조회하지 않으므로 여기서 status 를 써도 안전하다.
        if (overdue != null && overdue.getOverdueCount() > 0) {
            insights.add(new InsightResponseDTO(
                    "LOAN_OVERDUE",
                    metrics(
                            "overdueCount", overdue.getOverdueCount(),
                            "outstandingPrincipal", overdue.getOutstandingPrincipal()),
                    PRODUCTS_LINK));
        }

        // 그 달에 끝나는 상품만 한 줄씩. 이벤트가 없는 달엔 아무것도 넣지 않는다.
        // 늘 떠 있는 상태(현재 잔액, 가입 개수)를 여기 넣지 않는 이유는 이 화면이
        // '그 달에 무슨 일이 있었나'를 보는 곳이기 때문이다.
        for (ClosingProductVO product : closingProducts) {
            insights.add(new InsightResponseDTO(
                    "SAVING".equals(product.getProductType())
                            ? "SAVING_MATURED"
                            : "DEPOSIT_MATURED",
                    metrics(
                            "productName", product.getProductName(),
                            "productType", product.getProductType(),
                            "maturityDate", product.getMaturityDate(),
                            "closedOn", product.getClosedOn(),
                            "status", product.getStatus(),
                            "amount", product.getAmount()),
                    enrollmentLink(product.getProductType(), product.getEnrollmentId())));
        }

        boolean hasQuestActivity = quests.getCompletedCount() > 0
                || quests.getInProgressCount() > 0
                || quests.getFailedCount() > 0
                || quests.getExpiredCount() > 0
                || quests.getDeclinedCount() > 0;
        if (hasQuestActivity) {
            insights.add(new InsightResponseDTO(
                    "QUEST_COMPLETED",
                    metrics(
                            "completedCount", quests.getCompletedCount(),
                            "rewardAmount", flow.getEarnedAmount(),
                            "inProgressCount", quests.getInProgressCount(),
                            "failedCount", quests.getFailedCount(),
                            "expiredCount", quests.getExpiredCount(),
                            "declinedCount", quests.getDeclinedCount()),
                    "/child/quests"));
        }

        return insights;
    }

    /**
     * 기간 내 티니점수 변화. 이력이 하나도 없으면 null 이고 화면은 중립 한 줄만 보여준다.
     *
     * 대표 원인은 저장된 event_code 와 description 을 그대로 쓴다. 변동 폭이 큰 순서로
     * 최대 2개다 (정의서 6.6). 원인을 서버가 추정하거나 지어내지 않는다.
     */
    private MonthlyScoreResponseDTO toMonthlyScore(List<ScoreHistoryVO> rows) {
        if (rows.isEmpty()) {
            return null;
        }

        int net = 0;
        int increase = 0;
        int decrease = 0;
        for (ScoreHistoryVO row : rows) {
            net += row.getAmount();
            if (row.getAmount() > 0) {
                increase++;
            } else if (row.getAmount() < 0) {
                decrease++;
            }
        }

        List<MonthlyScoreReasonResponseDTO> topReasons = rows.stream()
                .sorted(Comparator.comparingInt(
                        (ScoreHistoryVO r) -> Math.abs(r.getAmount())).reversed())
                .limit(TOP_REASON_LIMIT)
                .map(r -> new MonthlyScoreReasonResponseDTO(
                        r.getEventCode(), r.getDescription(), r.getAmount()))
                .toList();

        return new MonthlyScoreResponseDTO(net, increase, decrease, topReasons);
    }

    /**
     * 가입 상품 상세로 가는 링크.
     *
     * 상품 종류를 경로에 넣는 이유는 id 가 테이블마다 따로 증가하기 때문이다.
     * 한 자녀가 예금 id=2 와 대출 id=2 를 동시에 가질 수 있어서, 종류 없이 id 만 쓰면
     * 예금 만기 카드를 눌렀는데 대출 상세가 열린다.
     * 경로 이름은 기존 API(/deposit-enrollments, /saving-enrollments, /loan-enrollments)를 따랐다.
     */
    private String enrollmentLink(String productType, Long enrollmentId) {
        return "/child/finance/" + productType.toLowerCase() + "-enrollments/" + enrollmentId;
    }

    /**
     * 키-값 쌍을 순서가 유지되는 맵으로. null 값도 그대로 담는다.
     *
     * 날짜는 여기서 문자열로 굳힌다. metrics 는 Map 이라 값에 @JsonFormat 을 걸 수 없고,
     * 그대로 두면 이 레포의 웹 ObjectMapper 가 LocalDate 를 [2026,9,5] 배열로 내보낸다.
     * DTO 필드는 @JsonFormat 으로 막았지만 맵 값은 여기서 막아야 한다.
     * LocalDate.toString() 이 곧 yyyy-MM-dd 다.
     */
    private Map<String, Object> metrics(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value instanceof LocalDate date) {
                value = date.toString();
            }
            map.put((String) keyValues[i], value);
        }
        return map;
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
