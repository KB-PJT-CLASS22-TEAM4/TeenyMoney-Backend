package com.teenyfin.teenymoney.domain.report.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.report.dto.response.MoneyReportResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.SpendingCategoryResponseDTO;
import com.teenyfin.teenymoney.domain.report.dto.response.WatchCategoryResponseDTO;
import com.teenyfin.teenymoney.domain.report.exception.MoneyReportErrorCode;
import com.teenyfin.teenymoney.domain.report.mapper.MoneyReportMapper;
import com.teenyfin.teenymoney.domain.report.vo.ChildProfileVO;
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
    @DisplayName("월 목록은 가입 월부터 현재 월까지")
    void availableMonths() {
        var months = service.getMoneyReport(CHILD, CHILD_ID, null).getAvailableMonths();

        assertThat(months).extracting(m -> m.getYearMonth())
                .containsExactly("2026-08", "2026-07", "2026-06",
                                 "2026-05", "2026-04", "2026-03", "2026-02", "2026-01");
    }
}
