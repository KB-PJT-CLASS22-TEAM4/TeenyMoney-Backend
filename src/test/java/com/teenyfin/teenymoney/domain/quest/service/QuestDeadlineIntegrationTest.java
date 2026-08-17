package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마감 배치의 실제 SQL 을 로컬 DB 에서 실행해 검증한다.
 *
 * 목 테스트가 확인할 수 없는 것만 여기서 본다. SKIP LOCKED 가 정말 건너뛰는지,
 * 인덱스를 타는지, 커밋 후 상태가 실제로 남는지는 문자열 검사로는 알 수 없다.
 *
 * 원격 DB 를 건드리지 않도록 localhost 연결일 때만 실행한다. 팀 공용 EC2 에 테스트
 * 퀘스트를 남기면 다른 사람의 화면에 나타난다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        QuestDeadlineTestConfig.class,
        QuestDeadlineService.class,
        TeenyScorePolicyService.class,
        TeenyScoreChangeService.class
})
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".*(localhost|127\\.0\\.0\\.1).*")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@Sql(scripts = "/quest/setup-quest-deadline-test.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/quest/cleanup-quest-deadline-test.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@DisplayName("퀘스트 마감 배치 DB 통합 테스트")
class QuestDeadlineIntegrationTest {

    /** 픽스처의 deadline 이 이 시각을 기준으로 배치되어 있다. */
    private static final LocalDateTime NOW = LocalDateTime.of(2000, 1, 2, 10, 0);

    @Autowired
    private QuestMapper questMapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private QuestDeadlineService questDeadlineService;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("기한이 지난 AVAILABLE 만 조회되고 PENDING·종료 상태는 제외된다")
    void selectsOnlyOverdueAvailableRows() {
        List<Long> ids = idsOf(questMapper.selectDeadlineTargetsForUpdate(
                QuestStatus.AVAILABLE, NOW, 200, Set.of()));

        assertThat(ids).contains(900001L, 900002L, 900003L);
        assertThat(ids)
                .doesNotContain(900011L)   // 아직 기한 전
                .doesNotContain(900020L)   // IN_PROGRESS - 이번 조회 대상 아님
                .doesNotContain(900021L)   // PENDING - 영구 제외(설계 15.4)
                .doesNotContain(900022L)   // COMPLETED
                .doesNotContain(900023L);  // EXPIRED
    }

    @Test
    @DisplayName("기한이 서버 시각과 같은 초인 퀘스트는 가져가지 않는다")
    void doesNotTakeQuestDueAtExactlyTheSameSecond() {
        List<Long> ids = idsOf(questMapper.selectDeadlineTargetsForUpdate(
                QuestStatus.AVAILABLE, NOW, 200, Set.of()));

        // 이 순간 자녀의 수락 요청은 허용된다(설계 15.4). 배치가 먼저 가져가면 뺏는 것이다.
        assertThat(ids).doesNotContain(900010L);

        // 1초만 지나면 대상이 된다.
        List<Long> afterOneSecond = idsOf(questMapper.selectDeadlineTargetsForUpdate(
                QuestStatus.AVAILABLE, NOW.plusSeconds(1), 200, Set.of()));
        assertThat(afterOneSecond).contains(900010L);
    }

    @Test
    @DisplayName("마감 임박 순으로 정렬되고 요청한 건수만 가져온다")
    void ordersByDeadlineAndRespectsLimit() {
        List<Long> ids = idsOf(questMapper.selectDeadlineTargetsForUpdate(
                QuestStatus.AVAILABLE, NOW, 2, Set.of()));

        assertThat(ids).hasSize(2);
        // 900001(08:00) 이 900002(09:00) 보다 먼저다. 오래 밀린 것부터 처리한다.
        assertThat(ids).containsExactly(900001L, 900002L);
    }

    @Test
    @DisplayName("실패로 표시된 ID 는 조회에서 빠지고 그 뒤의 정상 행이 올라온다")
    void excludedIdsLetLaterRowsIntoTheWindow() {
        // 실패한 행은 상태가 그대로라 (deadline, id) 정렬에서 계속 맨 앞을 차지한다.
        // 자바에서만 건너뛰면 조회 창이 앞으로 못 나가고 뒤의 정상 대상이 영원히 막힌다.
        List<Long> ids = idsOf(questMapper.selectDeadlineTargetsForUpdate(
                QuestStatus.AVAILABLE, NOW, 2, Set.of(900001L, 900002L)));

        assertThat(ids).containsExactly(900003L);
    }

    @Test
    @DisplayName("마감 조회는 deadline 인덱스를 타고 정렬을 따로 하지 않는다")
    void usesDeadlineIndexWithoutFilesort() {
        Map<String, Object> plan = jdbc().queryForMap(
                "EXPLAIN SELECT id FROM T_QST_BASE_M "
                        + "WHERE status = 'AVAILABLE' AND deadline < ? "
                        + "ORDER BY deadline ASC, id ASC LIMIT 200",
                Timestamp.valueOf(NOW));

        assertThat(String.valueOf(plan.get("key"))).isEqualTo("IX_QST_BASE_M_DEADLINE");
        // filesort 가 붙으면 행이 늘어날수록 매분 정렬 비용을 낸다.
        assertThat(String.valueOf(plan.get("Extra"))).doesNotContain("Using filesort");
    }

    @Test
    @DisplayName("SKIP LOCKED 는 다른 연결이 잠근 행을 기다리지 않고 건너뛴다")
    void skipLockedTakesDifferentRowsInsteadOfWaiting() throws Exception {
        // 연결 A 가 앞의 2건을 잠그고 트랜잭션을 열어 둔다.
        try (Connection locker = dataSource.getConnection()) {
            locker.setAutoCommit(false);
            List<Long> lockedByA = selectForUpdateSkipLocked(locker, 2);
            assertThat(lockedByA).containsExactly(900001L, 900002L);

            // 연결 B 는 A 가 잡은 행을 건너뛰고 그다음 행을 가져와야 한다.
            long started = System.nanoTime();
            try (Connection other = dataSource.getConnection()) {
                other.setAutoCommit(false);
                List<Long> lockedByB = selectForUpdateSkipLocked(other, 2);

                assertThat(lockedByB).doesNotContainAnyElementsOf(lockedByA);
                assertThat(lockedByB).contains(900003L);
                other.rollback();
            }
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

            // SKIP LOCKED 가 없으면 A 가 커밋할 때까지 대기한다(기본 50초 타임아웃).
            assertThat(elapsedMillis).isLessThan(5_000);
            locker.rollback();
        }
    }

    @Test
    @DisplayName("배치를 돌리면 상태와 종료 시각이 실제로 커밋된다")
    void closeExpiredCommitsStatusAndEndedAt() {
        int processed = questDeadlineService.closeExpired();
        assertThat(processed).isEqualTo(7);

        List<Map<String, Object>> rows = jdbc().queryForList(
                "SELECT id, status, remaining_count, ended_at FROM T_QST_BASE_M "
                        + "WHERE id IN (900001, 900002, 900003, 900020, "
                        + "900021, 900024, 900025, 900026)");

        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            if (id == 900021L) {
                // PENDING 은 기한이 지나도 부모가 검토할 수 있어야 한다.
                assertThat(row.get("status")).isEqualTo("PENDING");
                assertThat(row.get("ended_at")).isNull();
            } else if (id >= 900020L) {
                // 900025 는 점수 대상 자녀가 아니지만 마감 자체는 된다. 점수를 못 준다고
                // 되돌리면 기한이 지난 채로 남아 매 실행 다시 올라온다.
                assertThat(row.get("status")).isEqualTo("FAILED");
                assertThat(((Number) row.get("remaining_count")).intValue()).isZero();
                assertThat(row.get("ended_at")).isNotNull();
            } else {
                assertThat(row.get("status")).isEqualTo("EXPIRED");
                // 완료 탭 커서가 (ended_at DESC, id DESC) 라 비어 있으면 목록에서 사라진다.
                assertThat(row.get("ended_at")).isNotNull();
            }
        }

        assertThat(jdbc().queryForObject(
                "SELECT teeny_score FROM T_MBR_INFO_M WHERE id = -900002",
                Integer.class)).isEqualTo(608);
        assertThat(jdbc().queryForObject(
                "SELECT COUNT(*) FROM T_TNY_SCOREHIST_H "
                        + "WHERE child_id = -900002 AND event_key = 'QUEST_FAILED:900024'",
                Integer.class)).isEqualTo(1);
        // 마감은 됐지만 점수 이력은 남지 않아야 한다. 점수 SAVEPOINT 만 롤백된 것이다.
        assertThat(jdbc().queryForObject(
                "SELECT COUNT(*) FROM T_TNY_SCOREHIST_H "
                        + "WHERE event_key = 'QUEST_FAILED:900025'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("마감 알림이 전이별로 다른 수신자에게 쌓인다")
    void closeExpiredStoresNotificationsPerTransition() {
        questDeadlineService.closeExpired();

        // 수락되지 않은 채 만료된 건은 부모에게 간다.
        List<Map<String, Object>> parentRows = notificationsOf(-900001L, "자녀가 퀘스트를 시작하지 않았어요");
        assertThat(referenceIdsOf(parentRows)).containsExactly(900001L, 900002L, 900003L);
        // 다자녀 부모가 구분할 수 있도록 자녀 이름이 내용 앞에 붙는다.
        assertThat(String.valueOf(parentRows.get(0).get("content"))).contains(" · ");

        // 수행 중 만료된 건은 자녀에게 간다.
        List<Map<String, Object>> childRows = notificationsOf(-900002L, "퀘스트가 실패했어요");
        assertThat(referenceIdsOf(childRows)).containsExactly(900020L, 900024L, 900026L);

        // 점수 퀘스트만 차감 문구가 붙는다. 퀘스트명은 픽스처에서 오므로 접미사만 본다.
        assertThat(contentOf(childRows, 900024L)).endsWith(" · 티니점수가 차감됐어요");
        assertThat(contentOf(childRows, 900020L)).doesNotContain("티니점수");

        assertThat(parentRows).allSatisfy(row ->
                assertThat(row.get("reference_type")).isEqualTo("QUEST"));
        assertThat(childRows).allSatisfy(row ->
                assertThat(row.get("reference_type")).isEqualTo("QUEST"));
    }

    // ---------- 도우미 ----------

    private List<Map<String, Object>> notificationsOf(long memberId, String title) {
        return jdbc().queryForList(
                "SELECT title, content, reference_type, reference_id FROM T_NTF_NOTI_L "
                        + "WHERE member_id = ? AND title = ? ORDER BY reference_id",
                memberId, title);
    }

    private List<Long> referenceIdsOf(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> ((Number) row.get("reference_id")).longValue())
                .toList();
    }

    private String contentOf(List<Map<String, Object>> rows, long referenceId) {
        return rows.stream()
                .filter(row -> ((Number) row.get("reference_id")).longValue() == referenceId)
                .map(row -> String.valueOf(row.get("content")))
                .findFirst()
                .orElseThrow();
    }

    private List<Long> selectForUpdateSkipLocked(Connection connection, int limit)
            throws Exception {
        String sql = "SELECT id FROM T_QST_BASE_M "
                + "WHERE status = 'AVAILABLE' AND deadline < ? "
                + "ORDER BY deadline ASC, id ASC LIMIT ? FOR UPDATE SKIP LOCKED";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(NOW));
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getLong("id"));
                }
                return ids;
            }
        }
    }

    private List<Long> idsOf(List<QuestVO> quests) {
        return quests.stream().map(QuestVO::getId).toList();
    }
}
