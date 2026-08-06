package com.teenyfin.teenymoney.domain.teenyscore.mapper;

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
@ContextConfiguration(classes = RootConfig.class)
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
                "existsActiveConnection",
                "selectScoreForUpdate",
                "existsHistoryByEventKey",
                "updateTeenyScore",
                "insertScoreHistory")) {
            assertTrue(sqlSessionFactory.getConfiguration()
                    .hasStatement(NAMESPACE + "." + statement));
        }
    }
}
