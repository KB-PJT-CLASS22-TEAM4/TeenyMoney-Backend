package com.teenyfin.teenymoney.domain.family.mapper;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RootConfig;
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

/**
 * XML 경로나 네임스페이스가 어긋나면 기동 시점에 깨진다. 여기가 그 1차 방어선이다.
 * DB 는 필요 없다. 문장 등록과 SQL 모양만 본다.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class, initializers = LazyBeanInitializer.class)
@TestPropertySource(properties = {
        "jdbc.driver=com.mysql.cj.jdbc.Driver",
        "jdbc.url=jdbc:mysql://127.0.0.1:1/teenymoney",
        "jdbc.username=test",
        "jdbc.password=test"
})
class FamilyConnectionMapperContextTest {

    private static final String NAMESPACE = FamilyConnectionMapper.class.getName();

    private static final Map<String, Object> PARAMS = Map.of(
            "parentId", 1L,
            "childId", 2L,
            "now", LocalDateTime.of(2026, 8, 17, 10, 0));

    @Autowired
    private FamilyConnectionMapper familyConnectionMapper;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    @DisplayName("연동 관계 매퍼와 모든 문장이 등록된다")
    void registersMapperAndAllStatements() {
        assertNotNull(familyConnectionMapper);
        for (String statement : List.of("deactivate", "reactivate")) {
            assertTrue(sqlSessionFactory.getConfiguration()
                    .hasStatement(NAMESPACE + "." + statement), statement);
        }
    }

    @Test
    @DisplayName("해제는 활성 관계에만 적용된다")
    void deactivateAppliesOnlyToActiveConnection() {
        String sql = sql("deactivate");

        assertTrue(sql.contains("status = 'INACTIVE'"), sql);
        assertTrue(sql.contains("parent_id = ?"), sql);
        assertTrue(sql.contains("child_id = ?"), sql);
        // 이미 해제된 관계를 다시 해제하면 0 건이어야 호출부가 409 를 낼 수 있다.
        assertTrue(sql.contains("AND status = 'ACTIVE'"), sql);
    }

    @Test
    @DisplayName("재연결은 해제된 관계에만 적용된다")
    void reactivateAppliesOnlyToInactiveConnection() {
        String sql = sql("reactivate");

        assertTrue(sql.contains("status = 'ACTIVE'"), sql);
        // 처음 연결하는 쌍이면 0 건이 나와야 호출부가 INSERT 경로로 간다.
        assertTrue(sql.contains("AND status = 'INACTIVE'"), sql);
        assertFalse(sql.contains("INSERT"), sql);
    }

    private String sql(String statement) {
        return sqlSessionFactory.getConfiguration()
                .getMappedStatement(NAMESPACE + "." + statement)
                .getBoundSql(PARAMS)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }
}
