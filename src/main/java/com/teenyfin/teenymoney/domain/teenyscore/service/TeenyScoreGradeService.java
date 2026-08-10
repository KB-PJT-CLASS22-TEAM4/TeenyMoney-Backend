package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.domain.teenyscore.exception.TeenyScoreErrorCode;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** 실시간 점수와 분리된 월간 적용 등급을 현재 점수 기준으로 갱신한다. */
@Service
public class TeenyScoreGradeService {

    private final TeenyScoreMapper teenyScoreMapper;
    private final Clock clock;

    public TeenyScoreGradeService(
            TeenyScoreMapper teenyScoreMapper,
            Clock clock) {
        this.teenyScoreMapper = teenyScoreMapper;
        this.clock = clock;
    }

    @Transactional
    public int applyMonthlyGrades() {
        return teenyScoreMapper.updateAllActiveChildGrades(
                LocalDateTime.now(clock));
    }

    /** 신규 자녀의 저장된 초기 점수를 기준으로 최초 적용 등급을 부여한다. */
    @Transactional
    public void initializeGrade(Long childId) {
        int updatedCount = teenyScoreMapper.initializeAppliedGrade(
                childId, LocalDateTime.now(clock));
        if (updatedCount != 1) {
            throw new BusinessException(
                    TeenyScoreErrorCode.TEENY_SCORE_CHILD_NOT_FOUND);
        }
    }
}
