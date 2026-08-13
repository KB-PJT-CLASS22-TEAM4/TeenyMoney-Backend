package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.teenyscore.dto.request.TeenyScoreChangeRequestDTO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        QuestDeadlineTestConfig.class,
        QuestDeadlineService.class,
        TeenyScorePolicyService.class,
        QuestDeadlineSavepointIntegrationTest.FailureConfig.class
})
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".*(localhost|127\\.0\\.0\\.1).*")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@Sql(scripts = "/quest/setup-quest-deadline-test.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/quest/cleanup-quest-deadline-test.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@DisplayName("퀘스트 마감 SAVEPOINT DB 통합 테스트")
class QuestDeadlineSavepointIntegrationTest {

    @Autowired
    private QuestDeadlineService questDeadlineService;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("한 퀘스트의 데이터 오류는 롤백하고 같은 묶음의 다음 퀘스트는 커밋한다")
    void rollsBackOnlyFailedQuestAndCommitsNextQuest() {
        int processed = questDeadlineService.closeExpired();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(processed).isEqualTo(6);
        assertThat(jdbc.queryForMap(
                "SELECT status, remaining_count, ended_at FROM T_QST_BASE_M WHERE id = 900024"))
                .containsEntry("status", "IN_PROGRESS")
                .containsEntry("remaining_count", 3)
                .containsEntry("ended_at", null);
        assertThat(jdbc.queryForMap(
                "SELECT status, remaining_count FROM T_QST_BASE_M WHERE id = 900026"))
                .containsEntry("status", "FAILED")
                .containsEntry("remaining_count", 0);
    }

    @Configuration
    static class FailureConfig {

        @Bean
        TeenyScoreChangeService teenyScoreChangeService() {
            TeenyScoreChangeService service = mock(TeenyScoreChangeService.class);
            given(service.change(any())).willAnswer(invocation -> {
                TeenyScoreChangeRequestDTO request = invocation.getArgument(0);
                if (request.getReferenceId().equals(900024L)) {
                    throw new DataIntegrityViolationException("test score constraint failure");
                }
                return null;
            });
            return service;
        }
    }
}
