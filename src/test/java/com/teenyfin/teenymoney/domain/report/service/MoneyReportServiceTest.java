package com.teenyfin.teenymoney.domain.report.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.report.dto.response.InsightResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.MoneyReportResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.SpendingCategoryResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WatchCategoryResponseDTO;
import com.teenyfin.teenymoney.domain.report.exception.MoneyReportErrorCode;
import com.teenyfin.teenymoney.domain.report.mapper.MoneyReportMapper;
import com.teenyfin.teenymoney.domain.report.vo.ChildProfileVO;
import com.teenyfin.teenymoney.domain.report.vo.ClosingProductVO;
import com.teenyfin.teenymoney.domain.report.vo.LoanOverdueVO;
import com.teenyfin.teenymoney.domain.report.vo.LoanRepaymentVO;
import com.teenyfin.teenymoney.domain.report.vo.MoneyFlowVO;
import com.teenyfin.teenymoney.domain.report.vo.PermissionSummaryVO;
import com.teenyfin.teenymoney.domain.report.vo.ProductPeriodVO;
import com.teenyfin.teenymoney.domain.report.vo.QuestSummaryVO;
import com.teenyfin.teenymoney.domain.report.vo.ScoreHistoryVO;
import com.teenyfin.teenymoney.domain.report.vo.SpendingCategoryVO;
import com.teenyfin.teenymoney.domain.report.vo.SpendingTotalVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 조립 로직만 본다. 날짜 계산은 ReportPeriodCalculatorTest가, SQL은 MoneyReportMapperTest가 덮는다.
 *
 * 시계를 2026-08-13에 고정해서 언제 돌려도 같은 결과가 나오게 한다.
 */
class MoneyReportServiceTest {

    private static final Long CHILD_ID = 2L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 13);
    private static final MemberPrincipal CHILD = new MemberPrincipal(CHILD_ID, "CHILD");

    private MoneyReportMapper mapper;
    private FamilyAccessService familyAccessService;
    private MoneyReportService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MoneyReportMapper.class);
        familyAccessService = mock(FamilyAccessService.class);

        service = new MoneyReportService(
                mapper,
                new ReportPeriodCalculator(),
                familyAccessService,
                Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                            ZoneId.of("Asia/Seoul")));

        when(mapper.selectChildProfile(CHILD_ID)).thenReturn(
                ChildProfileVO.builder()
                        .id(CHILD_ID)
                        .role("CHILD")
                        .birthDate(LocalDate.of(2016, 3, 1))          // 만 10세 -> JUNIOR
                        .createdAt(LocalDateTime.of(2026, 1, 15, 0, 0))
                        .build());
        when(mapper.selectSpendingTotal(anyLong(), any(), any()))
                .thenReturn(SpendingTotalVO.builder().totalAmount(0).paymentCount(0).build());
        when(mapper.selectSpendingByCategory(anyLong(), any(), any())).thenReturn(List.of());
        when(mapper.selectDailySpending(anyLong(), any(), any())).thenReturn(List.of());

        // 2차 집계의 기본값은 "아무 활동 없음"이다. 각 테스트가 필요한 것만 덮어쓴다.
        when(mapper.selectMoneyFlow(anyLong(), any(), any()))
                .thenReturn(MoneyFlowVO.builder().build());
        when(mapper.selectLoanRepayment(anyLong(), any(), any()))
                .thenReturn(LoanRepaymentVO.builder().build());
        when(mapper.selectPermissionSummary(anyLong(), any(), any()))
                .thenReturn(PermissionSummaryVO.builder().build());
        when(mapper.selectQuestSummary(anyLong(), any(), any()))
                .thenReturn(QuestSummaryVO.builder().build());
        when(mapper.selectScoreHistory(anyLong(), any(), any())).thenReturn(List.of());
        when(mapper.selectActivityMonths(anyLong())).thenReturn(List.of());
        when(mapper.selectProductPeriods(anyLong())).thenReturn(List.of());
        when(mapper.selectClosingProducts(anyLong(), any(), any())).thenReturn(List.of());
        when(mapper.selectLoanOverdue(anyLong())).thenReturn(LoanOverdueVO.builder().build());
    }

    private void givenTotals(long amount, int count, long comparisonAmount, int comparisonCount) {
        when(mapper.selectSpendingTotal(eq(CHILD_ID),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 13))))
                .thenReturn(SpendingTotalVO.builder()
                        .totalAmount(amount).paymentCount(count).build());
        when(mapper.selectSpendingTotal(eq(CHILD_ID),
                eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 13))))
                .thenReturn(SpendingTotalVO.builder()
                        .totalAmount(comparisonAmount).paymentCount(comparisonCount).build());
    }

    private void givenCategories(List<SpendingCategoryVO> rows) {
        when(mapper.selectSpendingByCategory(eq(CHILD_ID),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 13)))).thenReturn(rows);
    }

    private SpendingCategoryVO row(
            Long id, String name, String policy, long amount, int count) {
        return SpendingCategoryVO.builder()
                .categoryId(id).categoryName(name).appliedPolicy(policy)
                .amount(amount).paymentCount(count).build();
    }

    @Test
    @DisplayName("childId를 받았으므로 권한 검증을 먼저 호출한다")
    void checksAccessFirst() {
        service.getMoneyReport(CHILD, CHILD_ID, null);

        verify(familyAccessService).requireChildAccess(CHILD, CHILD_ID);
    }

    @Test
    @DisplayName("권한이 없으면 집계하지 않고 그대로 403을 올려보낸다")
    void propagatesForbidden() {
        doThrow(new BusinessException(CommonErrorCode.AUTH_FORBIDDEN))
                .when(familyAccessService).requireChildAccess(any(), anyLong());

        assertThatThrownBy(() -> service.getMoneyReport(CHILD, CHILD_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.AUTH_FORBIDDEN);
    }

    @Test
    @DisplayName("자녀를 찾을 수 없으면 404")
    void childNotFound() {
        when(mapper.selectChildProfile(CHILD_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.getMoneyReport(CHILD, CHILD_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", MoneyReportErrorCode.MONEY_REPORT_CHILD_NOT_FOUND);
    }

    @Test
    @DisplayName("증감은 조회 기간에서 비교 기간을 뺀 값")
    void computesDelta() {
        givenTotals(38200, 7, 32100, 6);

        var spending = service.getMoneyReport(CHILD, CHILD_ID, "2026-08").getSpending();

        assertThat(spending.getTotalAmount()).isEqualTo(38200L);
        assertThat(spending.getComparisonAmount()).isEqualTo(32100L);
        assertThat(spending.getComparisonAmountDelta()).isEqualTo(6100L);
        assertThat(spending.getComparisonCountDelta()).isEqualTo(1);
    }

    @Test
    @DisplayName("카테고리는 금액 내림차순 → 횟수 내림차순 → 이름 오름차순")
    void sortsCategories() {
        givenTotals(400, 8, 0, 0);
        givenCategories(List.of(
                row(1L, "나중", "ALLOW", 100, 1),
                row(2L, "가나", "ALLOW", 100, 1),
                row(3L, "횟수많음", "ALLOW", 100, 3),
                row(4L, "금액많음", "ALLOW", 100, 1)));

        var categories = service.getMoneyReport(CHILD, CHILD_ID, "2026-08")
                .getSpending().getCategories();

        assertThat(categories).extracting(SpendingCategoryResponseDTO::getCategoryName)
                .containsExactly("횟수많음", "가나", "금액많음", "나중");
    }

    @Test
    @DisplayName("같은 카테고리가 정책별로 나뉘어 와도 목록에서는 한 줄로 합친다")
    void mergesSameCategoryAcrossPolicies() {
        givenTotals(30000, 3, 0, 0);
        givenCategories(List.of(
                row(9L, "게임", "WATCH", 20000, 2),
                row(9L, "게임", "ALLOW", 10000, 1)));

        var report = service.getMoneyReport(CHILD, CHILD_ID, "2026-08");

        // 카테고리 목록은 정책 무관 합산
        assertThat(report.getSpending().getCategories()).hasSize(1);
        assertThat(report.getSpending().getCategories().get(0).getAmount()).isEqualTo(30000L);
        assertThat(report.getSpending().getCategories().get(0).getPaymentCount()).isEqualTo(3);

        // 주의 업종은 WATCH 행만
        assertThat(report.getWatchSpending().getAmount()).isEqualTo(20000L);
        assertThat(report.getWatchSpending().getPaymentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("비중은 총액 기준 정수 %, 총액이 0이면 0으로 나누지 않는다")
    void ratio() {
        givenTotals(40000, 4, 0, 0);
        givenCategories(List.of(
                row(1L, "가", "ALLOW", 10000, 1),
                row(2L, "나", "ALLOW", 30000, 3)));

        var categories = service.getMoneyReport(CHILD, CHILD_ID, "2026-08")
                .getSpending().getCategories();

        assertThat(categories).extracting(SpendingCategoryResponseDTO::getRatio)
                .containsExactly(75, 25);
    }

    @Test
    @DisplayName("활동이 없는 달은 오류가 아니라 0과 빈 배열")
    void emptyMonthIsNotAnError() {
        MoneyReportResponseDTO report = service.getMoneyReport(CHILD, CHILD_ID, "2026-08");

        assertThat(report.getSpending().getTotalAmount()).isZero();
        assertThat(report.getSpending().getPaymentCount()).isZero();
        assertThat(report.getSpending().getCategories()).isEmpty();
        assertThat(report.getWatchSpending().getPaymentCount()).isZero();
        assertThat(report.getWatchSpending().getCategories()).isEmpty();
        // 막대 축은 그대로 있어야 한다
        assertThat(report.getSpending().getWeeklyTrend()).hasSize(6);
    }

    @Test
    @DisplayName("주의 업종은 금액 내림차순, 전체 결제 건수와 비교 기간 건수를 함께 준다")
    void watchSpending() {
        givenTotals(47000, 3, 0, 0);
        givenCategories(List.of(
                row(9L, "게임", "WATCH", 12000, 1),
                row(8L, "온라인쇼핑", "WATCH", 20000, 1),
                row(1L, "편의점", "ALLOW", 15000, 1)));
        when(mapper.selectSpendingByCategory(eq(CHILD_ID),
                eq(LocalDate.of(2026, 7, 1)), eq(LocalDate.of(2026, 7, 13))))
                .thenReturn(List.of(row(9L, "게임", "WATCH", 5000, 2)));

        var watch = service.getMoneyReport(CHILD, CHILD_ID, "2026-08").getWatchSpending();

        assertThat(watch.getPaymentCount()).isEqualTo(2);
        assertThat(watch.getAmount()).isEqualTo(32000L);
        assertThat(watch.getTotalPaymentCount()).isEqualTo(3);
        assertThat(watch.getComparisonCount()).isEqualTo(2);
        assertThat(watch.getCategories()).extracting(WatchCategoryResponseDTO::getCategoryName)
                .containsExactly("온라인쇼핑", "게임");
    }

    @Test
    @DisplayName("연령 모드는 조회한 달이 아니라 오늘 기준")
    void ageBandUsesToday() {
        when(mapper.selectChildProfile(CHILD_ID)).thenReturn(
                ChildProfileVO.builder()
                        .id(CHILD_ID).role("CHILD")
                        .birthDate(LocalDate.of(2013, 1, 1))          // 만 13세
                        .createdAt(LocalDateTime.of(2026, 1, 15, 0, 0))
                        .build());

        // 과거 달을 봐도 현재 연령을 적용한다
        assertThat(service.getMoneyReport(CHILD, CHILD_ID, "2026-02")
                .getAudience().getAgeBand()).isEqualTo("TEEN");
    }

    @Test
    @DisplayName("월 목록은 가입 월부터 현재 월까지, 활동 없는 달은 기록 없음")
    void availableMonths() {
        when(mapper.selectActivityMonths(CHILD_ID)).thenReturn(List.of("2026-07", "2026-03"));

        var months = service.getMoneyReport(CHILD, CHILD_ID, null).getAvailableMonths();

        assertThat(months).extracting(m -> m.getYearMonth())
                .containsExactly("2026-08", "2026-07", "2026-06",
                                 "2026-05", "2026-04", "2026-03", "2026-02", "2026-01");
        assertThat(months).extracting(m -> m.getStatus())
                .containsExactly("IN_PROGRESS", "COMPLETED", "NO_RECORD", "NO_RECORD",
                                 "NO_RECORD", "COMPLETED", "NO_RECORD", "NO_RECORD");
    }

    // ---- 2차: 한눈에 보기 / 인사이트 / 티니점수 ----------------------------------

    @Test
    @DisplayName("한눈에 보기는 예금·적금을 합쳐 모은 돈, 원금·이자를 합쳐 갚은 돈을 만든다")
    void summaryMergesFlows() {
        givenTotals(52000, 4, 0, 0);
        when(mapper.selectMoneyFlow(eq(CHILD_ID), any(), any())).thenReturn(
                MoneyFlowVO.builder().depositAmount(50000).savingAmount(10000)
                        .earnedAmount(4000).questRewardCount(1).build());
        when(mapper.selectLoanRepayment(eq(CHILD_ID), any(), any())).thenReturn(
                LoanRepaymentVO.builder().paidPrincipal(10000).paidInterest(1000)
                        .repaidCount(1).build());

        var summary = service.getMoneyReport(CHILD, CHILD_ID, "2026-08").getSummary();

        assertThat(summary.getSpentAmount()).isEqualTo(52000L);
        assertThat(summary.getSavedAmount()).isEqualTo(60000L);
        assertThat(summary.getDepositAmount()).isEqualTo(50000L);
        assertThat(summary.getSavingAmount()).isEqualTo(10000L);
        assertThat(summary.getEarnedAmount()).isEqualTo(4000L);
        assertThat(summary.getRepaidAmount()).isEqualTo(11000L);
        assertThat(summary.getRepaidPrincipal()).isEqualTo(10000L);
        assertThat(summary.getRepaidInterest()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("활동이 없으면 한눈에 보기는 전부 0이고 숨기지 않는다")
    void summaryStaysWithZeros() {
        var summary = service.getMoneyReport(CHILD, CHILD_ID, "2026-08").getSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.getSavedAmount()).isZero();
        assertThat(summary.getEarnedAmount()).isZero();
        assertThat(summary.getRepaidAmount()).isZero();
    }

    @Test
    @DisplayName("보여줄 사실이 없는 인사이트 카드는 아예 넣지 않는다")
    void insightsSkipEmptyFacts() {
        var insights = service.getMoneyReport(CHILD, CHILD_ID, "2026-08").getInsights();

        assertThat(insights).isEmpty();
    }

    @Test
    @DisplayName("인사이트는 화면 순서대로 나온다: 소비 → 소통 → 저축 → 상환 → 책임")
    void insightsAreOrdered() {
        givenTotals(10000, 1, 0, 0);
        givenCategories(List.of(row(1L, "편의점", "ALLOW", 10000, 1)));
        when(mapper.selectPermissionSummary(eq(CHILD_ID), any(), any())).thenReturn(
                PermissionSummaryVO.builder().requestCount(1).approvedCount(1)
                        .reasonWrittenCount(1).build());
        when(mapper.selectMoneyFlow(eq(CHILD_ID), any(), any())).thenReturn(
                MoneyFlowVO.builder().savingAmount(10000).savingProductCount(1)
                        .savingPaymentCount(1).build());
        when(mapper.selectLoanRepayment(eq(CHILD_ID), any(), any())).thenReturn(
                LoanRepaymentVO.builder().paidPrincipal(10000).paidInterest(1000)
                        .repaidCount(1).build());
        when(mapper.selectQuestSummary(eq(CHILD_ID), any(), any())).thenReturn(
                QuestSummaryVO.builder().completedCount(2).inProgressCount(1).build());

        var insights = service.getMoneyReport(CHILD, CHILD_ID, "2026-08").getInsights();

        assertThat(insights).extracting(InsightResponseDTO::getInsightCode)
                .containsExactly("SPENDING_TOP_CATEGORY", "PERMISSION_STATUS_SUMMARY",
                                 "SAVING_PAYMENT", "LOAN_REPAYMENT",
                                 "QUEST_COMPLETED");
    }

    @Test
    @DisplayName("저축 카드는 상품을 고르지 않고 그 달에 넣은 총액과 상품 개수를 준다")
    void savingCardIsMonthlyFlowAcrossProducts() {
        // 적금 2개에 각각 넣고 예금에도 넣은 달
        when(mapper.selectMoneyFlow(eq(CHILD_ID), any(), any())).thenReturn(
                MoneyFlowVO.builder()
                        .savingAmount(20000).savingProductCount(2).savingPaymentCount(3)
                        .depositAmount(50000).depositProductCount(1).depositPaymentCount(1)
                        .build());

        var insight = findInsight("SAVING_PAYMENT");

        assertThat(insight.getMetrics())
                .containsEntry("amount", 70000L)          // 적금 20,000 + 예금 50,000
                .containsEntry("savingAmount", 20000L)
                .containsEntry("savingProductCount", 2)   // 상품 하나만 고르지 않는다
                .containsEntry("savingPaymentCount", 3)
                .containsEntry("depositAmount", 50000L);
        assertThat(insight.getDeepLink()).isEqualTo("/child/finance/my-products");
    }

    @Test
    @DisplayName("상환 카드는 그 달에 갚은 금액을 원금과 이자로 나눠 준다")
    void loanCardIsMonthlyRepaidAmount() {
        when(mapper.selectLoanRepayment(eq(CHILD_ID), any(), any())).thenReturn(
                LoanRepaymentVO.builder().paidPrincipal(10000).paidInterest(1000)
                        .repaidCount(1).build());

        assertThat(findInsight("LOAN_REPAYMENT").getMetrics())
                .containsEntry("amount", 11000L)
                .containsEntry("principal", 10000L)
                .containsEntry("interest", 1000L)
                .containsEntry("repaidCount", 1);
    }

    @Test
    @DisplayName("그 달에 넣거나 갚은 게 없으면 저축·상환 카드가 없다")
    void noSavingOrLoanCardWhenNothingMoved() {
        var codes = service.getMoneyReport(CHILD, CHILD_ID, "2026-08").getInsights().stream()
                .map(InsightResponseDTO::getInsightCode).toList();

        // 가입 상품이 있어도 그 달에 움직이지 않았으면 카드가 안 나온다
        assertThat(codes).doesNotContain("SAVING_PAYMENT", "LOAN_REPAYMENT");
    }

    @Test
    @DisplayName("연체는 진행 중인 달에만 붙는다")
    void overdueOnlyOnCurrentMonth() {
        when(mapper.selectLoanOverdue(CHILD_ID)).thenReturn(
                LoanOverdueVO.builder().overdueCount(1).outstandingPrincipal(40000).build());

        var current = service.getMoneyReport(CHILD, CHILD_ID, "2026-08").getInsights();
        assertThat(current).extracting(InsightResponseDTO::getInsightCode)
                .contains("LOAN_OVERDUE");
        assertThat(findInsight("LOAN_OVERDUE").getMetrics())
                .containsEntry("overdueCount", 1)
                .containsEntry("outstandingPrincipal", 40000L);

        // 과거 달에는 조회조차 하지 않는다. 오늘의 연체가 지난 달 리포트로 새면 안 된다.
        var past = service.getMoneyReport(CHILD, CHILD_ID, "2026-07").getInsights();
        assertThat(past).extracting(InsightResponseDTO::getInsightCode)
                .doesNotContain("LOAN_OVERDUE");
    }

    @Test
    @DisplayName("티니점수는 순변동과 증감 건수를 세고 대표 원인을 변동 폭 순으로 2개만 준다")
    void teenyScore() {
        when(mapper.selectScoreHistory(eq(CHILD_ID), any(), any())).thenReturn(List.of(
                score(3, "QUEST_COMPLETED", "퀘스트 성공"),
                score(-4, "LOAN_INSTALLMENT_OVERDUE", "대출 월별 상환 결과"),
                score(-2, "QUEST_FAILED", "퀘스트 최종 실패"),
                score(1, "ETC", "기타")));

        var score = service.getMoneyReport(CHILD, CHILD_ID, "2026-08").getTeenyScore();

        assertThat(score.getNetChange()).isEqualTo(-2);
        assertThat(score.getIncreaseEventCount()).isEqualTo(2);
        assertThat(score.getDecreaseEventCount()).isEqualTo(2);
        assertThat(score.getTopReasons()).hasSize(2);
        assertThat(score.getTopReasons()).extracting(r -> r.getEventCode())
                .containsExactly("LOAN_INSTALLMENT_OVERDUE", "QUEST_COMPLETED");
    }

    @Test
    @DisplayName("기간 내 점수 이력이 없으면 teenyScore 는 null")
    void teenyScoreNullWhenNoHistory() {
        assertThat(service.getMoneyReport(CHILD, CHILD_ID, "2026-08").getTeenyScore()).isNull();
    }

    @Test
    @DisplayName("그 달에 끝나는 상품이 있을 때만 만기 카드가 붙는다")
    void maturityCardOnlyWhenSomethingCloses() {
        // 평소에는 없다. 늘 떠 있는 잔액을 여기서 보여주지 않기 때문이다.
        assertThat(service.getMoneyReport(CHILD, CHILD_ID, "2026-08").getInsights())
                .extracting(InsightResponseDTO::getInsightCode)
                .doesNotContain("DEPOSIT_MATURED", "SAVING_MATURED");

        when(mapper.selectClosingProducts(eq(CHILD_ID), any(), any())).thenReturn(List.of(
                ClosingProductVO.builder()
                        .productType("DEPOSIT").enrollmentId(7L).productName("튼튼 정기예금")
                        .status("ACTIVE").maturityDate(LocalDate.of(2026, 8, 31))
                        .amount(50000).build()));

        var insight = findInsight("DEPOSIT_MATURED");
        assertThat(insight.getMetrics())
                .containsEntry("productName", "튼튼 정기예금")
                .containsEntry("amount", 50000L)
                .containsEntry("maturityDate", "2026-08-31");
        assertThat(insight.getDeepLink()).isEqualTo("/child/finance/deposit-enrollments/7");
    }

    @Test
    @DisplayName("만기 카드 딥링크에는 상품 종류가 들어간다 — id 는 테이블마다 따로 증가해서 겹친다")
    void maturityDeepLinksAreDisambiguatedByProductType() {
        // 한 자녀가 예금 id=2, 적금 id=2 를 동시에 가질 수 있다 (실제 시드가 그렇다)
        when(mapper.selectClosingProducts(eq(CHILD_ID), any(), any())).thenReturn(List.of(
                ClosingProductVO.builder().productType("DEPOSIT").enrollmentId(2L)
                        .status("ACTIVE").maturityDate(LocalDate.of(2026, 8, 31)).build(),
                ClosingProductVO.builder().productType("SAVING").enrollmentId(2L)
                        .status("MATURED").maturityDate(LocalDate.of(2026, 8, 20)).build()));

        var links = service.getMoneyReport(CHILD, CHILD_ID, "2026-08").getInsights().stream()
                .map(InsightResponseDTO::getDeepLink)
                .filter(l -> l.contains("enrollments"))
                .toList();

        assertThat(links).containsExactlyInAnyOrder(
                "/child/finance/deposit-enrollments/2",
                "/child/finance/saving-enrollments/2");
    }

    @Test
    @DisplayName("적금 만기는 SAVING_MATURED 로 구분된다")
    void savingMaturityHasItsOwnCode() {
        when(mapper.selectClosingProducts(eq(CHILD_ID), any(), any())).thenReturn(List.of(
                ClosingProductVO.builder()
                        .productType("SAVING").enrollmentId(21L).productName("티니 자유적금")
                        .status("MATURED").maturityDate(LocalDate.of(2026, 8, 20))
                        .closedOn(LocalDate.of(2026, 8, 20)).amount(60000).build()));

        assertThat(findInsight("SAVING_MATURED").getMetrics())
                .containsEntry("status", "MATURED")
                .containsEntry("closedOn", "2026-08-20");
    }

    private InsightResponseDTO findInsight(String code) {
        return service.getMoneyReport(CHILD, CHILD_ID, "2026-08").getInsights().stream()
                .filter(i -> code.equals(i.getInsightCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(code + " 인사이트가 없습니다"));
    }

    private ScoreHistoryVO score(int amount, String eventCode, String description) {
        return ScoreHistoryVO.builder()
                .amount(amount).eventCode(eventCode).description(description)
                .createdAt(LocalDateTime.of(2026, 8, 10, 12, 0)).build();
    }
}
