package com.teenyfin.teenymoney.domain.teenyscore.mapper;

import com.teenyfin.teenymoney.config.RootConfig;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreHistoryVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreMonthlyHistoryVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@Transactional
class TeenyScoreMapperTest {

    @Autowired
    private TeenyScoreMapper teenyScoreMapper;

    @Test
    void selectTeenyScoreByChildIdReturnsScoreAndGrade() {
        TeenyScoreVO score = teenyScoreMapper.selectTeenyScoreByChildId(2L);

        assertNotNull(score);
        assertEquals(2L, score.getChildId());
        assertEquals(610, score.getTeenyScore());
        assertEquals("양호", score.getGradeName());
        assertEquals(new BigDecimal("0.20"), score.getBonusRate());
    }

    @Test
    void selectTeenyScoreByUnknownChildReturnsNull() {
        assertNull(teenyScoreMapper.selectTeenyScoreByChildId(Long.MAX_VALUE));
    }

    @Test
    void selectHistoriesByChildIdReturnsNewestFirst() {
        List<TeenyScoreHistoryVO> histories =
                teenyScoreMapper.selectHistoriesByChildId(2L);

        assertFalse(histories.isEmpty());
        for (int index = 0; index < histories.size() - 1; index++) {
            TeenyScoreHistoryVO current = histories.get(index);
            TeenyScoreHistoryVO next = histories.get(index + 1);
            assertTrue(current.getCreatedAt().isAfter(next.getCreatedAt())
                    || current.getCreatedAt().isEqual(next.getCreatedAt()));
        }
    }

    @Test
    void selectAllGradesReturnsHighestGradeFirst() {
        List<TeenyScoreGradeVO> grades = teenyScoreMapper.selectAllGrades();

        assertFalse(grades.isEmpty());
        for (int index = 0; index < grades.size() - 1; index++) {
            assertTrue(grades.get(index).getMinScore()
                    > grades.get(index + 1).getMinScore());
        }
    }

    @Test
    void selectMonthlyHistoriesByChildIdReturnsMonthlyLastScore() {
        List<TeenyScoreMonthlyHistoryVO> histories =
                teenyScoreMapper.selectMonthlyHistoriesByChildId(2L);

        assertFalse(histories.isEmpty());
        assertEquals("2026-06", histories.get(0).getYearMonth());
        assertEquals(610, histories.get(0).getTeenyScore());
    }

    @Test
    void existsActiveConnectionReturnsRelationshipStatus() {
        assertTrue(teenyScoreMapper.existsActiveConnection(1L, 2L));
        assertFalse(teenyScoreMapper.existsActiveConnection(1L, Long.MAX_VALUE));
    }
}
