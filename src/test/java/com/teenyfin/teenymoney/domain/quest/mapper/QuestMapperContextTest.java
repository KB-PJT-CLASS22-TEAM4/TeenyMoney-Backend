package com.teenyfin.teenymoney.domain.quest.mapper;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.quest.vo.DeclineReasonCode;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class, initializers = LazyBeanInitializer.class)
@TestPropertySource(properties = {
        "jdbc.driver=com.mysql.cj.jdbc.Driver",
        "jdbc.url=jdbc:mysql://127.0.0.1:1/teenymoney",
        "jdbc.username=test",
        "jdbc.password=test"
})
class QuestMapperContextTest {

    private static final String NAMESPACE = QuestMapper.class.getName();

    @Autowired
    private QuestMapper questMapper;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    @DisplayName("퀘스트 매퍼와 모든 문장이 등록된다")
    void registersMapperAndAllStatements() {
        assertNotNull(questMapper);
        for (String statement : List.of(
                "selectByCreationRequestKey",
                "selectByIdForUpdateByParent",
                "selectByIdForUpdateByChild",
                "insert",
                "updateAvailable",
                "updateStatusByChild",
                "updateDeclineByChild",
                "deleteAvailable",
                "selectPageByParent",
                "selectPageByChild",
                "selectDetailByParent",
                "selectDetailByChild",
                "selectLatestVerification",
                "selectLatestVerificationForUpdate",
                "updateVerificationReview",
                "updateCompletedByParent",
                "updateAfterRejectionByParent",
                "insertVerification",
                "selectDeadlineTargetsForUpdate",
                "updateStatusForDeadline")) {
            assertTrue(sqlSessionFactory.getConfiguration()
                    .hasStatement(NAMESPACE + "." + statement), statement);
        }
    }

    @Test
    @DisplayName("부모와 자녀 목록 SQL은 각자의 범위를 직접 제한한다")
    void parentAndChildListSqlRestrictTheirOwnScope() {
        Map<String, Object> parentParams = Map.of(
                "memberId", 1L,
                "childId", 2L,
                "statuses", List.of(QuestStatus.AVAILABLE),
                "completed", false,
                "limit", 21);
        Map<String, Object> childParams = Map.of(
                "memberId", 2L,
                "statuses", List.of(QuestStatus.AVAILABLE),
                "completed", false,
                "limit", 21);

        String parentSql = sql("selectPageByParent", parentParams);
        String childSql = sql("selectPageByChild", childParams);

        assertTrue(parentSql.contains("q.parent_id = ?"), parentSql);
        assertTrue(parentSql.contains("q.child_id = ?"), parentSql);
        assertTrue(childSql.contains("q.child_id = ?"), childSql);
        assertFalse(childSql.contains("q.parent_id = ?"), childSql);
    }

    @Test
    @DisplayName("탭에 따라 정렬 키가 달라진다")
    void sortKeyDiffersByTab() {
        Map<String, Object> available = Map.of(
                "memberId", 1L,
                "statuses", List.of(QuestStatus.AVAILABLE),
                "completed", false,
                "limit", 21);
        Map<String, Object> completed = Map.of(
                "memberId", 1L,
                "statuses", List.of(QuestStatus.COMPLETED),
                "completed", true,
                "limit", 21);

        assertTrue(sql("selectPageByParent", available)
                .contains("ORDER BY q.deadline ASC, q.id ASC"));
        assertTrue(sql("selectPageByParent", completed)
                .contains("ORDER BY q.ended_at DESC, q.id DESC"));
    }

    @Test
    @DisplayName("자녀 잠금 조회는 자녀 범위만 제한하고 행을 잠근다")
    void childLockingSelectRestrictsChildScopeAndLocksRow() {
        String locking = sql("selectByIdForUpdateByChild", Map.of(
                "questId", 55L,
                "childId", 2L));

        assertTrue(locking.contains("q.child_id = ?"), locking);
        assertFalse(locking.contains("q.parent_id = ?"), locking);
        assertTrue(locking.contains("FOR UPDATE"), locking);
    }

    @Test
    @DisplayName("자녀 상태 변경 SQL은 기대 상태일 때만 적용된다")
    void childStatusUpdateAppliesOnlyOnExpectedStatus() {
        String promote = sql("updateStatusByChild", Map.of(
                "questId", 55L,
                "childId", 2L,
                "fromStatus", QuestStatus.AVAILABLE,
                "toStatus", QuestStatus.IN_PROGRESS,
                "updatedAt", LocalDateTime.of(2026, 8, 10, 10, 0)));

        assertTrue(promote.contains("child_id = ?"), promote);
        assertTrue(promote.contains("SET status = ?"), promote);
        assertTrue(promote.contains("AND status = ?"), promote);
        assertFalse(promote.contains("parent_id"), promote);
    }

    @Test
    @DisplayName("상세 조회 SQL에 사용하지 않는 수락 시각이 없다")
    void detailSqlHasNoUnusedAcceptedAt() {
        String detailSql = sql("selectDetailByParent", Map.of(
                "questId", 55L,
                "memberId", 1L));

        assertFalse(detailSql.contains("accepted_at"), detailSql);
    }

    @Test
    @DisplayName("최신 인증 잠금 조회는 최신 시도 한 건을 잠근다")
    void latestVerificationSelectLocksLatestAttempt() {
        String locking = sql("selectLatestVerificationForUpdate",
                Map.of("questId", 55L));

        assertTrue(locking.contains("ORDER BY attempt_no DESC, id DESC"), locking);
        assertTrue(locking.contains("LIMIT 1"), locking);
        assertTrue(locking.contains("FOR UPDATE"), locking);
    }

    @Test
    @DisplayName("인증 심사 갱신은 해당 퀘스트의 PENDING 인증 한 건만 변경한다")
    void verificationReviewUpdateUsesPendingGuard() {
        String update = sql("updateVerificationReview", Map.of(
                "verificationId", 9L,
                "questId", 55L,
                "status", "APPROVED",
                "reviewedAt", LocalDateTime.of(2026, 8, 12, 2, 0)));

        assertTrue(update.contains("id = ?"), update);
        assertTrue(update.contains("quest_id = ?"), update);
        assertTrue(update.contains("status = 'PENDING'"), update);
    }

    @Test
    @DisplayName("부모 완료 갱신은 PENDING 퀘스트에만 적용된다")
    void parentCompletionUpdateUsesPendingGuard() {
        String update = sql("updateCompletedByParent", Map.of(
                "questId", 55L,
                "parentId", 1L,
                "endedAt", LocalDateTime.of(2026, 8, 12, 2, 0),
                "updatedAt", LocalDateTime.of(2026, 8, 12, 2, 0)));

        assertTrue(update.contains("status = 'COMPLETED'"), update);
        assertTrue(update.contains("parent_id = ?"), update);
        assertTrue(update.contains("AND status = 'PENDING'"), update);
    }

    @Test
    @DisplayName("반려 상태 갱신은 남은 횟수와 선택적 연장 기한을 원자적으로 반영한다")
    void rejectionUpdateChangesStateCountAndOptionalDeadline() {
        String update = sql("updateAfterRejectionByParent", Map.of(
                "questId", 55L,
                "parentId", 1L,
                "toStatus", QuestStatus.IN_PROGRESS,
                "remainingCount", 2,
                "deadline", LocalDateTime.of(2026, 8, 13, 20, 0),
                "updatedAt", LocalDateTime.of(2026, 8, 12, 2, 0)));

        assertTrue(update.contains("status = ?"), update);
        assertTrue(update.contains("remaining_count = ?"), update);
        assertTrue(update.contains("deadline = COALESCE(?, deadline)"), update);
        assertTrue(update.contains("parent_id = ?"), update);
        assertTrue(update.contains("AND status = 'PENDING'"), update);
    }

    @Test
    @DisplayName("마감 대상 조회는 인덱스 순서로 정렬하고 잠긴 행을 건너뛴다")
    void deadlineTargetSelectUsesIndexOrderAndSkipsLockedRows() {
        String sql = sql("selectDeadlineTargetsForUpdate", Map.of(
                "status", QuestStatus.AVAILABLE,
                "now", LocalDateTime.of(2026, 8, 10, 10, 0),
                "limit", 200));

        assertTrue(sql.contains("q.status = ?"), sql);
        assertTrue(sql.contains("q.deadline < ?"), sql);
        assertFalse(sql.contains("q.deadline <= ?"), sql);
        assertTrue(sql.contains("ORDER BY q.deadline ASC, q.id ASC"), sql);
        assertTrue(sql.contains("FOR UPDATE SKIP LOCKED"), sql);
        assertFalse(sql.contains("NOT IN"), sql);
    }

    @Test
    @DisplayName("이번 실행에서 실패한 퀘스트는 마감 대상 조회에서 제외된다")
    void deadlineTargetSelectExcludesFailedQuestIds() {
        // 실패해도 상태가 그대로라 (deadline, id) 정렬 맨 앞에 계속 남는다. 자바에서만
        // 건너뛰면 조회 창이 앞으로 나가지 못해 뒤의 정상 대상이 영원히 처리되지 않는다.
        String sql = sql("selectDeadlineTargetsForUpdate", Map.of(
                "status", QuestStatus.AVAILABLE,
                "now", LocalDateTime.of(2026, 8, 10, 10, 0),
                "limit", 200,
                "excludeIds", List.of(11L, 22L)));

        assertTrue(sql.contains("q.id NOT IN"), sql);
        assertTrue(sql.contains("FOR UPDATE SKIP LOCKED"), sql);
    }

    @Test
    @DisplayName("마감 상태 변경은 기대 상태일 때만 적용되고 종료 시각을 남긴다")
    void deadlineStatusUpdateAppliesOnlyOnExpectedStatusAndSetsEndedAt() {
        String sql = sql("updateStatusForDeadline", Map.of(
                "questId", 55L,
                "fromStatus", QuestStatus.AVAILABLE,
                "toStatus", QuestStatus.EXPIRED,
                "remainingCount", 0,
                "endedAt", LocalDateTime.of(2026, 8, 10, 10, 0)));

        assertTrue(sql.contains("AND status = ?"), sql);
        assertTrue(sql.contains("ended_at = ?"), sql);
        assertTrue(sql.contains("remaining_count = COALESCE(?, remaining_count)"), sql);
        assertFalse(sql.contains("child_id"), sql);
        assertFalse(sql.contains("parent_id"), sql);
    }

    private String sql(String statement, Map<String, Object> params) {
        return sqlSessionFactory.getConfiguration()
                .getMappedStatement(NAMESPACE + "." + statement)
                .getBoundSql(params)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Test
    @DisplayName("거절 SQL은 사유와 종료 시각을 함께 기록한다")
    void declineSqlRecordsReasonAndEndedAt() {
        String decline = sql("updateDeclineByChild", Map.of(
                "questId", 55L,
                "childId", 2L,
                "reasonCode", DeclineReasonCode.OTHER,
                "reasonDetail", "사유",
                "endedAt", LocalDateTime.of(2026, 8, 10, 10, 0)));

        assertTrue(decline.contains("status = 'DECLINED'"), decline);
        assertTrue(decline.contains("ended_at = ?"), decline);
        assertTrue(decline.contains("child_id = ?"), decline);
        assertTrue(decline.contains("AND status = 'AVAILABLE'"), decline);
        assertFalse(decline.contains("parent_id"), decline);
    }
}
