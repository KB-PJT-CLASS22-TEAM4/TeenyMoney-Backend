package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.teenyscore.dto.request.TeenyScoreChangeRequestDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreChangeResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreEventCode;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        RootConfig.class,
        TeenyScoreChangeService.class
})
@EnabledIfEnvironmentVariable(
        named = "DB_URL",
        matches = ".*(localhost|127\\.0\\.0\\.1).*"
)
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_DRIVER", matches = ".+")
@DisplayName("티니점수 공통 변경 서비스 DB 통합 테스트")
@Sql(
        scripts = "/teenyscore/setup-teeny-score-test.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
        scripts = "/teenyscore/cleanup-teeny-score-test.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class TeenyScoreChangeServiceIntegrationTest {

    private static final long TEST_CHILD_ID = 900002L;

    @Autowired
    private TeenyScoreChangeService teenyScoreChangeService;

    @Autowired
    private TeenyScoreMapper teenyScoreMapper;

    @Test
    @Transactional
    @DisplayName("실제 DB에서 점수와 이력을 한 번만 저장하고 동일 이벤트 재호출을 차단한다")
    void changePersistsScoreAndHistoryOnlyOnceForSameEvent() {
        int scoreBefore = teenyScoreMapper.selectScoreForUpdate(
                TEST_CHILD_ID);
        String eventKey = "TEST:DEPOSIT_MATURED:" + UUID.randomUUID();
        TeenyScoreChangeRequestDTO request =
                new TeenyScoreChangeRequestDTO(
                        TEST_CHILD_ID,
                        TeenyScoreEventCode.DEPOSIT_MATURED,
                        6,
                        eventKey,
                        "통합 테스트 예금 만기",
                        "DEPOSIT_ENROLLMENT",
                        1L);

        TeenyScoreChangeResponseDTO first =
                teenyScoreChangeService.change(request);
        TeenyScoreChangeResponseDTO duplicate =
                teenyScoreChangeService.change(request);

        int expectedScore = Math.min(1000, scoreBefore + 6);
        assertTrue(first.isApplied());
        assertEquals(expectedScore, first.getScoreAfter());
        assertFalse(duplicate.isApplied());
        assertEquals(expectedScore, duplicate.getScoreAfter());
        assertEquals(expectedScore,
                teenyScoreMapper.selectScoreForUpdate(TEST_CHILD_ID));
        assertTrue(teenyScoreMapper.existsHistoryByEventKey(
                TEST_CHILD_ID, eventKey));
    }

    @Test
    @DisplayName("이력 저장에 실패하면 먼저 변경한 회원 점수를 롤백한다")
    void historyInsertFailureRollsBackScoreUpdate() {
        int scoreBefore = teenyScoreMapper.selectScoreForUpdate(
                TEST_CHILD_ID);
        String eventKey = "TEST:ROLLBACK:" + UUID.randomUUID();
        TeenyScoreChangeRequestDTO request =
                new TeenyScoreChangeRequestDTO(
                        TEST_CHILD_ID,
                        TeenyScoreEventCode.DEPOSIT_MATURED,
                        6,
                        eventKey,
                        "통합 테스트 트랜잭션 롤백",
                        "INVALID_REFERENCE_TYPE",
                        1L);

        assertThrows(
                PersistenceException.class,
                () -> teenyScoreChangeService.change(request));

        assertEquals(
                scoreBefore,
                teenyScoreMapper.selectScoreForUpdate(TEST_CHILD_ID));
        assertFalse(teenyScoreMapper.existsHistoryByEventKey(
                TEST_CHILD_ID, eventKey));
    }
}
