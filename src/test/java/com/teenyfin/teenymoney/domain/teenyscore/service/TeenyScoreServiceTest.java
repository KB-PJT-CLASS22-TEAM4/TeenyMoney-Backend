package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreGradeResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreHistoryResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.exception.TeenyScoreErrorCode;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreHistoryVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeenyScoreServiceTest {

    private TeenyScoreMapper teenyScoreMapper;
    private TeenyScoreService teenyScoreService;

    @BeforeEach
    void setUp() {
        teenyScoreMapper = mock(TeenyScoreMapper.class);
        teenyScoreService = new TeenyScoreService(teenyScoreMapper);
    }

    @Test
    void getTeenyScoreReturnsScoreAndGrade() {
        when(teenyScoreMapper.selectTeenyScoreByChildId(2L))
                .thenReturn(teenyScore());

        TeenyScoreResponseDTO response =
                teenyScoreService.getTeenyScore(2L);

        assertEquals(2L, response.getChildId());
        assertEquals(610, response.getTeenyScore());
        assertEquals(4L, response.getGradeId());
        assertEquals("양호", response.getGradeName());
        assertEquals(600, response.getMinScore());
        assertEquals(799, response.getMaxScore());
        assertEquals(new BigDecimal("0.20"), response.getBonusRate());
        assertEquals("#4CAF50", response.getColor());
    }

    @Test
    void getTeenyScoreWithMissingChildThrowsException() {
        when(teenyScoreMapper.selectTeenyScoreByChildId(999L))
                .thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> teenyScoreService.getTeenyScore(999L));

        assertEquals(
                TeenyScoreErrorCode.TEENY_SCORE_CHILD_NOT_FOUND,
                exception.getErrorCode());
    }

    @Test
    void getHistoriesReturnsConvertedHistoryList() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 25, 9, 0);
        when(teenyScoreMapper.selectTeenyScoreByChildId(2L))
                .thenReturn(teenyScore());
        when(teenyScoreMapper.selectHistoriesByChildId(2L))
                .thenReturn(List.of(history(createdAt)));

        List<TeenyScoreHistoryResponseDTO> histories =
                teenyScoreService.getHistories(2L);

        assertEquals(1, histories.size());
        assertEquals(1L, histories.get(0).getHistoryId());
        assertEquals(10, histories.get(0).getAmount());
        assertEquals(610, histories.get(0).getScoreAfter());
        assertEquals("적금납입성공", histories.get(0).getDescription());
        assertEquals(createdAt, histories.get(0).getCreatedAt());
    }

    @Test
    void getHistoriesWithNoHistoryReturnsEmptyList() {
        when(teenyScoreMapper.selectTeenyScoreByChildId(2L))
                .thenReturn(teenyScore());
        when(teenyScoreMapper.selectHistoriesByChildId(2L))
                .thenReturn(List.of());

        List<TeenyScoreHistoryResponseDTO> histories =
                teenyScoreService.getHistories(2L);

        assertTrue(histories.isEmpty());
    }

    @Test
    void getHistoriesWithMissingChildDoesNotQueryHistories() {
        when(teenyScoreMapper.selectTeenyScoreByChildId(999L))
                .thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> teenyScoreService.getHistories(999L));

        assertEquals(
                TeenyScoreErrorCode.TEENY_SCORE_CHILD_NOT_FOUND,
                exception.getErrorCode());
        verify(teenyScoreMapper, never()).selectHistoriesByChildId(999L);
    }

    @Test
    void getGradesReturnsConvertedGradeList() {
        when(teenyScoreMapper.selectAllGrades())
                .thenReturn(List.of(grade()));

        List<TeenyScoreGradeResponseDTO> grades =
                teenyScoreService.getGrades();

        assertEquals(1, grades.size());
        assertEquals(4L, grades.get(0).getGradeId());
        assertEquals("양호", grades.get(0).getGradeName());
        assertEquals(new BigDecimal("0.20"), grades.get(0).getBonusRate());
    }

    @Test
    void getGradesWithNoGradeDataThrowsException() {
        when(teenyScoreMapper.selectAllGrades()).thenReturn(List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> teenyScoreService.getGrades());

        assertEquals(
                TeenyScoreErrorCode.TEENY_SCORE_GRADE_NOT_FOUND,
                exception.getErrorCode());
    }

    private TeenyScoreVO teenyScore() {
        TeenyScoreVO teenyScore = new TeenyScoreVO();
        teenyScore.setChildId(2L);
        teenyScore.setTeenyScore(610);
        teenyScore.setGradeId(4L);
        teenyScore.setGradeName("양호");
        teenyScore.setMinScore(600);
        teenyScore.setMaxScore(799);
        teenyScore.setBonusRate(new BigDecimal("0.20"));
        teenyScore.setColor("#4CAF50");
        return teenyScore;
    }

    private TeenyScoreHistoryVO history(LocalDateTime createdAt) {
        TeenyScoreHistoryVO history = new TeenyScoreHistoryVO();
        history.setHistoryId(1L);
        history.setAmount(10);
        history.setScoreAfter(610);
        history.setDescription("적금납입성공");
        history.setCreatedAt(createdAt);
        return history;
    }

    private TeenyScoreGradeVO grade() {
        TeenyScoreGradeVO grade = new TeenyScoreGradeVO();
        grade.setGradeId(4L);
        grade.setGradeName("양호");
        grade.setMinScore(600);
        grade.setMaxScore(799);
        grade.setBonusRate(new BigDecimal("0.20"));
        grade.setColor("#4CAF50");
        return grade;
    }
}
