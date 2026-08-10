package com.teenyfin.teenymoney.domain.teenyscore.mapper;

import com.teenyfin.teenymoney.config.LazyBeanInitializer;
import com.teenyfin.teenymoney.config.RootConfig;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
// 이 테스트는 매퍼 등록과 SQL 문만 확인한다. RootConfig가 스캔하는 서비스 계층까지
// 생성하면 S3·Redis 같은 무관한 의존이 필요해지므로 지연 생성으로 둔다.
@ContextConfiguration(classes = RootConfig.class, initializers = LazyBeanInitializer.class)
@TestPropertySource(properties = {
        "jdbc.driver=com.mysql.cj.jdbc.Driver",
        "jdbc.url=jdbc:mysql://127.0.0.1:1/teenymoney",
        "jdbc.username=test",
        "jdbc.password=test"
})
class TeenyScoreMapperContextTest {

    private static final String NAMESPACE = TeenyScoreMapper.class.getName();

    @Autowired
    private TeenyScoreMapper teenyScoreMapper;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void teenyScoreMapperAndStatementsAreRegistered() {
        assertNotNull(teenyScoreMapper);

        for (String statement : List.of(
                "selectTeenyScoreByChildId",
                "selectHistoriesByChildId",
                "selectMonthlyHistoriesByChildId",
                "selectAllGrades",
                "selectScoreForUpdate",
                "existsHistoryByEventKey",
                "updateTeenyScore",
                "updateAllActiveChildGrades",
                "initializeAppliedGrade",
                "insertScoreHistory")) {
            assertTrue(sqlSessionFactory.getConfiguration()
                    .hasStatement(NAMESPACE + "." + statement));
        }
    }
}
