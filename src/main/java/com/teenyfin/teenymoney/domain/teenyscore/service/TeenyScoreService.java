package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreGradeResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreHistoryResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.exception.TeenyScoreErrorCode;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeenyScoreService {

    private final TeenyScoreMapper teenyScoreMapper;

    public TeenyScoreService(TeenyScoreMapper teenyScoreMapper) {
        this.teenyScoreMapper = teenyScoreMapper;
    }

    public TeenyScoreResponseDTO getTeenyScore(Long childId) {
        TeenyScoreVO teenyScore = findTeenyScore(childId);
        return TeenyScoreResponseDTO.of(teenyScore);
    }

    public List<TeenyScoreHistoryResponseDTO> getHistories(Long childId) {
        findTeenyScore(childId);

        return teenyScoreMapper.selectHistoriesByChildId(childId)
                .stream()
                .map(TeenyScoreHistoryResponseDTO::of)
                .toList();
    }

    public List<TeenyScoreGradeResponseDTO> getGrades() {
        List<TeenyScoreGradeVO> grades = teenyScoreMapper.selectAllGrades();

        if (grades.isEmpty()) {
            throw new BusinessException(
                    TeenyScoreErrorCode.TEENY_SCORE_GRADE_NOT_FOUND);
        }

        return grades.stream()
                .map(TeenyScoreGradeResponseDTO::of)
                .toList();
    }

    private TeenyScoreVO findTeenyScore(Long childId) {
        TeenyScoreVO teenyScore =
                teenyScoreMapper.selectTeenyScoreByChildId(childId);

        if (teenyScore == null) {
            throw new BusinessException(
                    TeenyScoreErrorCode.TEENY_SCORE_CHILD_NOT_FOUND);
        }

        return teenyScore;
    }
}
