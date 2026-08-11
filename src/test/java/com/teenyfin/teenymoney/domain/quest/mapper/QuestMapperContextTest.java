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
                "deleteAvailable",
                "selectPageByParent",
                "selectPageByChild",
                "selectDetailByParent",
                "selectDetailByChild",
                "selectLatestVerification")) {
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
