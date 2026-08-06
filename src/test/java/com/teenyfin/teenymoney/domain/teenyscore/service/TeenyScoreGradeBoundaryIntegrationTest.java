package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
@EnabledIfEnvironmentVariable(named = "DB_DRIVER", matches = ".+")
@EnabledIfEnvironmentVariable(
        named = "DB_URL",
        matches = ".*(localhost|127\\.0\\.0\\.1).*"
)
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@Transactional
@DisplayName("티니점수 등급 경계 DB 통합 테스트")
@Sql(
        scripts = "/teenyscore/setup-teeny-score-test.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
        scripts = "/teenyscore/cleanup-teeny-score-test.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class TeenyScoreGradeBoundaryIntegrationTest {

    private static final long TEST_CHILD_ID = 900002L;

    @Autowired
    private TeenyScoreMapper teenyScoreMapper;

    @Test
    @DisplayName("0점부터 1000점까지 등급별 최소·최대 경계를 정확하게 조회한다")
    void scoreBoundariesReturnExpectedGrades() {
        assertGrade(0, "새싹");
        assertGrade(449, "새싹");
        assertGrade(450, "스타터");
        assertGrade(649, "스타터");
        assertGrade(650, "플러스");
        assertGrade(749, "플러스");
        assertGrade(750, "프로");
        assertGrade(899, "프로");
        assertGrade(900, "마스터");
        assertGrade(1000, "마스터");
    }

    private void assertGrade(int score, String expectedGradeName) {
        assertEquals(1, teenyScoreMapper.updateTeenyScore(
                TEST_CHILD_ID, score));

        TeenyScoreVO result = teenyScoreMapper.selectTeenyScoreByChildId(
                TEST_CHILD_ID);

        assertNotNull(result);
        assertEquals(expectedGradeName, result.getGradeName());
    }
}
