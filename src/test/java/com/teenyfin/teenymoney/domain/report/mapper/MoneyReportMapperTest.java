package com.teenyfin.teenymoney.domain.report.mapper;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.report.vo.DailySpendingVO;
import com.teenyfin.teenymoney.domain.report.vo.SpendingCategoryVO;
import com.teenyfin.teenymoney.domain.report.vo.SpendingTotalVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * sql/seed/02_seed_money_report_demo.sql 이 깔린 로컬 DB 기준 검증.
 *
 * 기대값은 sql/seed/03_validate_money_report_demo.sql 과 같다.
 *
 * 금액 단언은 현재 월이 아니라 완료 월(전월)에 건다. 시드의 현재 월 결제는 그 달 4~10일에만
 * 찍히는데 진행 중인 달의 조회 기간은 1일부터 오늘까지다. 그 달 9일 이전에 이 테스트를 돌리면
 * 현재 월 합계가 시드 기대값보다 작게 나온다. 결함이 아니라 정의서 4.2대로의 동작이므로
 * 기간이 1일~말일로 고정된 완료 월을 단언 대상으로 삼는다.
 *
 * 주차 관련 단언은 여기 두지 않는다. 진행 중인 달의 주차 수가 실행 시점에 따라 달라진다.
 * 주차는 ReportPeriodCalculatorTest 가 고정 날짜로 덮는다.
 */
@ExtendWith(SpringExtension.class)
// LazyBeanInitializer 가 없으면 RootConfig 의 서비스 계층이 전부 즉시 생성되면서
// RestTemplate 같은 이 테스트와 무관한 의존까지 요구한다.
@ContextConfiguration(classes = RootConfig.class, initializers = LazyBeanInitializer.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@Transactional
class MoneyReportMapperTest {

    private static final String JUNIOR = "report-junior@gmail.com";
    private static final String TEEN = "report-teen@gmail.com";
    private static final String EMPTY = "report-empty@gmail.com";

    @Autowired
    private MoneyReportMapper moneyReportMapper;

    private JdbcTemplate jdbcTemplate;

    @Autowired
    void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /** 전월 1일. 시드가 CURDATE() 기준 상대 날짜라 여기서도 그렇게 잡는다. */
    private LocalDate previousMonthStart;
    private LocalDate previousMonthEnd;

    @BeforeEach
    void setUp() {
        LocalDate firstOfThisMonth = LocalDate.now().withDayOfMonth(1);
        previousMonthStart = firstOfThisMonth.minusMonths(1);
        previousMonthEnd = firstOfThisMonth.minusDays(1);

        // 데모 시드가 안 깔린 DB에서는 조용히 건너뛴다. 없는 데이터로 실패시키면
        // 정작 무엇이 문제인지 알기 어렵다.
        assumeTrue(memberIdOrNull(JUNIOR) != null,
                "money report demo seed(02_seed_money_report_demo.sql)가 필요합니다");
    }

    private Long memberIdOrNull(String email) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM T_MBR_INFO_M WHERE email = ?", Long.class, email);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private SpendingTotalVO previousMonthTotal(String email) {
        return moneyReportMapper.selectSpendingTotal(
                memberIdOrNull(email), previousMonthStart, previousMonthEnd);
    }

    @Test
    @DisplayName("전월 총 지출이 시드 기대값과 일치한다")
    void previousMonthTotalsMatchSeed() {
        SpendingTotalVO junior = previousMonthTotal(JUNIOR);
        assertThat(junior.getTotalAmount()).isEqualTo(35_000L);
        assertThat(junior.getPaymentCount()).isEqualTo(4);

        SpendingTotalVO teen = previousMonthTotal(TEEN);
        assertThat(teen.getTotalAmount()).isEqualTo(52_000L);
        assertThat(teen.getPaymentCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("활동이 없는 자녀는 0원 0건이 나온다 (null 아님)")
    void emptyChildReturnsZero() {
        SpendingTotalVO empty = previousMonthTotal(EMPTY);

        assertThat(empty).isNotNull();
        assertThat(empty.getTotalAmount()).isZero();
        assertThat(empty.getPaymentCount()).isZero();
    }

    @Test
    @DisplayName("SUCCESS가 아닌 결제는 집계에서 빠진다")
    void ignoresNonSuccessPayments() {
        Long teenId = memberIdOrNull(TEEN);

        Long successOnly = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM T_PAY_TRAN_L p "
                        + "JOIN T_WLT_BASE_M w ON p.wallet_id = w.id AND w.type = 'MEMBER' "
                        + "WHERE w.member_id = ? AND p.status = 'SUCCESS' "
                        + "AND p.created_at >= ? AND p.created_at < ?",
                Long.class, teenId, previousMonthStart, previousMonthEnd.plusDays(1));

        Long allStatuses = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM T_PAY_TRAN_L p "
                        + "JOIN T_WLT_BASE_M w ON p.wallet_id = w.id AND w.type = 'MEMBER' "
                        + "WHERE w.member_id = ? "
                        + "AND p.created_at >= ? AND p.created_at < ?",
                Long.class, teenId, previousMonthStart, previousMonthEnd.plusDays(1));

        // 시드에 FAILED/REJECTED가 섞여 있어야 이 테스트가 의미를 가진다
        assumeTrue(allStatuses != null && successOnly != null && allStatuses > successOnly,
                "전월 구간에 실패/거절 결제가 없어 필터를 검증할 수 없습니다");

        assertThat(previousMonthTotal(TEEN).getPaymentCount())
                .isEqualTo(successOnly.intValue());
    }

    @Test
    @DisplayName("주의 업종은 WATCH 정책 행으로 구분된다")
    void watchRowsAreDistinguishable() {
        List<SpendingCategoryVO> rows = moneyReportMapper.selectSpendingByCategory(
                memberIdOrNull(TEEN), previousMonthStart, previousMonthEnd);

        long watchCount = rows.stream()
                .filter(r -> "WATCH".equals(r.getAppliedPolicy()))
                .mapToInt(SpendingCategoryVO::getPaymentCount)
                .sum();
        long watchAmount = rows.stream()
                .filter(r -> "WATCH".equals(r.getAppliedPolicy()))
                .mapToLong(SpendingCategoryVO::getAmount)
                .sum();

        assertThat(watchCount).isEqualTo(2);
        assertThat(watchAmount).isEqualTo(27_000L);

        // 주니어는 전월에 주의 업종 결제가 없다
        assertThat(moneyReportMapper.selectSpendingByCategory(
                memberIdOrNull(JUNIOR), previousMonthStart, previousMonthEnd))
                .noneMatch(r -> "WATCH".equals(r.getAppliedPolicy()));
    }

    @Test
    @DisplayName("카테고리 합계가 총액과 일치하고 이름이 비지 않는다")
    void categorySumMatchesTotal() {
        List<SpendingCategoryVO> rows = moneyReportMapper.selectSpendingByCategory(
                memberIdOrNull(TEEN), previousMonthStart, previousMonthEnd);

        assertThat(rows.stream().mapToLong(SpendingCategoryVO::getAmount).sum())
                .isEqualTo(previousMonthTotal(TEEN).getTotalAmount());
        assertThat(rows).allSatisfy(
                r -> assertThat(r.getCategoryName()).isNotBlank());
    }

    @Test
    @DisplayName("일자별 합계가 총액과 일치하고 조회 기간을 벗어나지 않는다")
    void dailySumMatchesTotal() {
        List<DailySpendingVO> rows = moneyReportMapper.selectDailySpending(
                memberIdOrNull(TEEN), previousMonthStart, previousMonthEnd);

        assertThat(rows.stream().mapToLong(DailySpendingVO::getAmount).sum())
                .isEqualTo(previousMonthTotal(TEEN).getTotalAmount());
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getSpentDate()).isAfterOrEqualTo(previousMonthStart);
            assertThat(r.getSpentDate()).isBeforeOrEqualTo(previousMonthEnd);
        });
    }

    @Test
    @DisplayName("조회 기간 마지막 날의 결제가 빠지지 않는다")
    void includesLastDayOfPeriod() {
        Long teenId = memberIdOrNull(TEEN);

        // 전월에 실제로 결제가 있었던 마지막 날을 찾아, 그 날 하루만 조회해도 잡히는지 본다.
        List<DailySpendingVO> rows = moneyReportMapper.selectDailySpending(
                teenId, previousMonthStart, previousMonthEnd);
        assumeTrue(!rows.isEmpty(), "전월 결제가 없어 검증할 수 없습니다");

        DailySpendingVO lastDay = rows.get(rows.size() - 1);
        SpendingTotalVO singleDay = moneyReportMapper.selectSpendingTotal(
                teenId, lastDay.getSpentDate(), lastDay.getSpentDate());

        assertThat(singleDay.getTotalAmount()).isEqualTo(lastDay.getAmount());
        assertThat(singleDay.getPaymentCount()).isEqualTo(lastDay.getPaymentCount());
    }

    @Test
    @DisplayName("전월 모은 돈: 틴은 예금 50,000 / 주니어는 적금 10,000")
    void moneyFlowSavedAmount() {
        var teen = moneyReportMapper.selectMoneyFlow(
                memberIdOrNull(TEEN), previousMonthStart, previousMonthEnd);
        assertThat(teen.getDepositAmount()).isEqualTo(50_000L);
        assertThat(teen.getSavingAmount()).isZero();

        var junior = moneyReportMapper.selectMoneyFlow(
                memberIdOrNull(JUNIOR), previousMonthStart, previousMonthEnd);
        assertThat(junior.getDepositAmount()).isZero();
        assertThat(junior.getSavingAmount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("적금·예금 납입 개수와 횟수를 함께 센다")
    void moneyFlowCountsProductsAndPayments() {
        var junior = moneyReportMapper.selectMoneyFlow(
                memberIdOrNull(JUNIOR), previousMonthStart, previousMonthEnd);

        // 주니어는 전월에 적금 1개에 1회 납입
        assertThat(junior.getSavingProductCount()).isEqualTo(1);
        assertThat(junior.getSavingPaymentCount()).isEqualTo(1);
        assertThat(junior.getSavingAmount()).isEqualTo(10_000L);
        assertThat(junior.getDepositProductCount()).isZero();

        // 틴은 전월에 예금 1개에 1회
        var teen = moneyReportMapper.selectMoneyFlow(
                memberIdOrNull(TEEN), previousMonthStart, previousMonthEnd);
        assertThat(teen.getDepositProductCount()).isEqualTo(1);
        assertThat(teen.getDepositPaymentCount()).isEqualTo(1);
        assertThat(teen.getSavingProductCount()).isZero();
    }

    @Test
    @DisplayName("전월 직접 얻은 돈은 자녀 지갑으로 들어온 퀘스트 보상만 센다")
    void moneyFlowEarnedAmount() {
        var teen = moneyReportMapper.selectMoneyFlow(
                memberIdOrNull(TEEN), previousMonthStart, previousMonthEnd);

        assertThat(teen.getEarnedAmount()).isEqualTo(4_000L);
        assertThat(teen.getQuestRewardCount()).isEqualTo(1);

        // 대출 상환도 자녀 지갑에서 나가는 이체지만 모은 돈에 섞이면 안 된다
        assertThat(teen.getDepositAmount() + teen.getSavingAmount()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("전월 갚은 돈은 원금과 이자로 나뉜다")
    void loanRepaymentSplitsPrincipalAndInterest() {
        var teen = moneyReportMapper.selectLoanRepayment(
                memberIdOrNull(TEEN), previousMonthStart, previousMonthEnd);

        assertThat(teen.getPaidPrincipal()).isEqualTo(10_000L);
        assertThat(teen.getPaidInterest()).isEqualTo(1_000L);
        assertThat(teen.getRepaidCount()).isEqualTo(1);

        // 주니어는 대출이 없다
        var junior = moneyReportMapper.selectLoanRepayment(
                memberIdOrNull(JUNIOR), previousMonthStart, previousMonthEnd);
        assertThat(junior.getPaidPrincipal()).isZero();
        assertThat(junior.getRepaidCount()).isZero();
    }

    @Test
    @DisplayName("오늘만 허용은 상태별로 세고 사유 작성 여부도 센다")
    void permissionSummary() {
        var teen = moneyReportMapper.selectPermissionSummary(
                memberIdOrNull(TEEN), previousMonthStart, previousMonthEnd);

        assertThat(teen.getRequestCount()).isEqualTo(1);
        assertThat(teen.getApprovedCount()).isEqualTo(1);
        assertThat(teen.getRejectedCount()).isZero();
        assertThat(teen.getReasonWrittenCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("퀘스트는 ended_at 이 기간 안에 있는 것만 종료로 센다")
    void questSummary() {
        var teen = moneyReportMapper.selectQuestSummary(
                memberIdOrNull(TEEN), previousMonthStart, previousMonthEnd);

        assertThat(teen.getCompletedCount()).isEqualTo(1);
        assertThat(teen.getFailedCount()).isZero();   // 실패 퀘스트는 이번 달 것이다
    }

    @Test
    @DisplayName("달을 넘겨 끝난 퀘스트는 이전 달에 '진행 중'으로 잡힌다")
    void questInProgressIsReconstructedAsOfPeriodEnd() {
        Long juniorId = memberIdOrNull(JUNIOR);

        // 시드의 퀘스트는 전부 같은 달에 생성·종료돼서 이 경계를 가리지 못한다.
        // 이번 달에 끝난 퀘스트 하나를 지난 달 생성으로 옮겨 만든다. (@Transactional 롤백)
        Long questId = jdbcTemplate.queryForObject(
                "SELECT id FROM T_QST_BASE_M WHERE child_id = ? AND status = 'COMPLETED' "
                        + "AND ended_at >= ? ORDER BY ended_at DESC LIMIT 1",
                Long.class, juniorId, LocalDate.now().withDayOfMonth(1));
        jdbcTemplate.update("UPDATE T_QST_BASE_M SET created_at = ? WHERE id = ?",
                previousMonthStart.plusDays(10).atStartOfDay(), questId);

        var previousMonth = moneyReportMapper.selectQuestSummary(
                juniorId, previousMonthStart, previousMonthEnd);
        var thisMonth = moneyReportMapper.selectQuestSummary(
                juniorId, LocalDate.now().withDayOfMonth(1), LocalDate.now());

        // 지난 달 말 기준으로는 아직 안 끝난 퀘스트다
        assertThat(previousMonth.getInProgressCount()).isEqualTo(1);
        assertThat(previousMonth.getCompletedCount()).isEqualTo(1);   // 지난 달에 끝난 다른 퀘스트

        // 이번 달에는 완료로 잡히고 진행 중에서는 빠진다
        assertThat(thisMonth.getCompletedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("가입 상품이 살아 있던 기간을 읽는다")
    void productPeriods() {
        var periods = moneyReportMapper.selectProductPeriods(memberIdOrNull(JUNIOR));

        assertThat(periods).isNotEmpty();
        assertThat(periods).allSatisfy(p -> assertThat(p.getStartDate()).isNotNull());

        // 활동이 전혀 없는 자녀는 상품도 없다 — '기록 없음' 판정이 성립한다
        assertThat(moneyReportMapper.selectProductPeriods(memberIdOrNull(EMPTY))).isEmpty();
    }

    @Test
    @DisplayName("티니점수 이력은 event_code 와 함께 읽힌다")
    void scoreHistory() {
        var rows = moneyReportMapper.selectScoreHistory(
                memberIdOrNull(JUNIOR), previousMonthStart, previousMonthEnd);

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getEventCode()).isNotBlank();
            assertThat(r.getDescription()).isNotBlank();
            assertThat(r.getCreatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("활동 월 목록은 결제가 없어도 저축·퀘스트가 있으면 잡는다")
    void activityMonthsCoverAllDomains() {
        var months = moneyReportMapper.selectActivityMonths(memberIdOrNull(JUNIOR));

        assertThat(months).isNotEmpty();
        assertThat(months).allSatisfy(m -> assertThat(m).matches("\\d{4}-\\d{2}"));
        // 활동이 전혀 없는 자녀는 빈 목록이어야 '기록 없음' 판정이 성립한다
        assertThat(moneyReportMapper.selectActivityMonths(memberIdOrNull(EMPTY))).isEmpty();
    }

    @Test
    @DisplayName("만기가 이번 달 밖이면 빈 목록이다")
    void noClosingProductsWhenMaturityIsFarAway() {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

        // 시드 값에 기대지 않고 이 테스트 안에서 만기를 멀리 밀어둔다.
        // (@Transactional 이라 롤백된다. 누군가 시드 만기를 이번 달로 바꿔도 안 깨진다)
        jdbcTemplate.update("UPDATE T_DPT_ENROLL_M SET maturity_date = ? WHERE child_id = ?",
                monthStart.plusMonths(6), memberIdOrNull(TEEN));
        jdbcTemplate.update("UPDATE T_SVG_ENROLL_M SET maturity_date = ? WHERE child_id = ?",
                monthStart.plusMonths(6), memberIdOrNull(JUNIOR));

        assertThat(moneyReportMapper.selectClosingProducts(
                memberIdOrNull(TEEN), monthStart, monthEnd)).isEmpty();
        assertThat(moneyReportMapper.selectClosingProducts(
                memberIdOrNull(JUNIOR), monthStart, monthEnd)).isEmpty();
    }

    @Test
    @DisplayName("만기일이 이번 달로 오면 잡힌다 (@Transactional 로 롤백된다)")
    void picksUpProductMaturingThisMonth() {
        Long teenId = memberIdOrNull(TEEN);
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

        jdbcTemplate.update(
                "UPDATE T_DPT_ENROLL_M SET maturity_date = ? WHERE child_id = ?",
                monthEnd, teenId);

        var closing = moneyReportMapper.selectClosingProducts(teenId, monthStart, monthEnd);

        assertThat(closing).hasSize(1);
        assertThat(closing.get(0).getProductType()).isEqualTo("DEPOSIT");
        assertThat(closing.get(0).getMaturityDate()).isEqualTo(monthEnd);
        assertThat(closing.get(0).getAmount()).isEqualTo(50_000L);
        assertThat(closing.get(0).getProductName()).isNotBlank();
    }

    @Test
    @DisplayName("적금도 같은 규칙으로 잡힌다")
    void picksUpSavingMaturingThisMonth() {
        Long juniorId = memberIdOrNull(JUNIOR);
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

        jdbcTemplate.update(
                "UPDATE T_SVG_ENROLL_M SET maturity_date = ? WHERE child_id = ?",
                monthStart.plusDays(3), juniorId);

        var closing = moneyReportMapper.selectClosingProducts(juniorId, monthStart, monthEnd);

        assertThat(closing).hasSize(1);
        assertThat(closing.get(0).getProductType()).isEqualTo("SAVING");
        assertThat(closing.get(0).getAmount()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("자녀 프로필에서 연령 모드와 가입 월에 쓸 값을 읽는다")
    void readsChildProfile() {
        var profile = moneyReportMapper.selectChildProfile(memberIdOrNull(JUNIOR));

        assertThat(profile).isNotNull();
        assertThat(profile.getRole()).isEqualTo("CHILD");
        assertThat(profile.getBirthDate()).isNotNull();
        assertThat(profile.getCreatedAt()).isNotNull();
    }
}
