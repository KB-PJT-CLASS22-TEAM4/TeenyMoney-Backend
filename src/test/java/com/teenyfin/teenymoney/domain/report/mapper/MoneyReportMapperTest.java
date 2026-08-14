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
    @DisplayName("자녀 프로필에서 연령 모드와 가입 월에 쓸 값을 읽는다")
    void readsChildProfile() {
        var profile = moneyReportMapper.selectChildProfile(memberIdOrNull(JUNIOR));

        assertThat(profile).isNotNull();
        assertThat(profile.getRole()).isEqualTo("CHILD");
        assertThat(profile.getBirthDate()).isNotNull();
        assertThat(profile.getCreatedAt()).isNotNull();
    }
}
