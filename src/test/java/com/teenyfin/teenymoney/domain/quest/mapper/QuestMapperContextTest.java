package com.teenyfin.teenymoney.domain.quest.mapper;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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
    void 퀘스트_매퍼와_모든_문장이_등록된다() {
        assertNotNull(questMapper);
        for (String statement : List.of(
                "selectByCreationRequestKey",
                "selectByIdForUpdateByParent",
                "insert",
                "updateAvailable",
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
    void 부모와_자녀_목록_SQL은_각자의_범위를_직접_제한한다() {
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
    void 탭에_따라_정렬_키가_달라진다() {
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

    private String sql(String statement, Map<String, Object> params) {
        return sqlSessionFactory.getConfiguration()
                .getMappedStatement(NAMESPACE + "." + statement)
                .getBoundSql(params)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }
}
