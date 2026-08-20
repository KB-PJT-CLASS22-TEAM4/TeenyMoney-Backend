package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.domain.teenyscore.dto.request.TeenyScoreChangeRequestDTO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.response.TeenyScoreChangeResponseDTO;
import com.teenyfin.teenymoney.domain.teenyscore.exception.TeenyScoreErrorCode;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeenyScoreChangeService {

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 1000;

    private final TeenyScoreMapper teenyScoreMapper;

    public TeenyScoreChangeService(TeenyScoreMapper teenyScoreMapper) {
        this.teenyScoreMapper = teenyScoreMapper;
    }

    /**
     * 같은 자녀에게 동시에 발생한 여러 금융 이벤트의 이력 판정을 직렬화한다.
     * 호출한 트랜잭션이 끝날 때까지 자녀 점수 행 잠금을 유지한다.
     */
    @Transactional
    public void lockChildScore(Long childId) {
        Integer score = teenyScoreMapper.selectScoreForUpdate(childId);
        if (score == null) {
            throw new BusinessException(
                    TeenyScoreErrorCode.TEENY_SCORE_CHILD_NOT_FOUND);
        }
    }

    /**
     * 자녀 행을 잠근 뒤 중복 확인, 점수 변경, 이력 저장을 한 트랜잭션으로 처리한다.
     * 이력 amount에는 요청량이 아니라 0~1000 보정 후 실제 반영량을 저장한다.
     */
    @Transactional
    public TeenyScoreChangeResponseDTO change(
            TeenyScoreChangeRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("request는 필수입니다.");
        }

        Integer scoreBefore = teenyScoreMapper.selectScoreForUpdate(
                request.getChildId());
        if (scoreBefore == null) {
            throw new BusinessException(
                    TeenyScoreErrorCode.TEENY_SCORE_CHILD_NOT_FOUND);
        }

        if (teenyScoreMapper.existsHistoryByEventKey(
                request.getChildId(), request.getEventKey())) {
            return TeenyScoreChangeResponseDTO.duplicate(
                    request.getChildId(), scoreBefore, request.getAmount());
        }

        int scoreAfter = clampScore(scoreBefore, request.getAmount());
        int appliedAmount = scoreAfter - scoreBefore;

        if (appliedAmount != 0) {
            teenyScoreMapper.updateTeenyScore(
                    request.getChildId(), scoreAfter);
        }
        teenyScoreMapper.insertScoreHistory(
                request.getChildId(),
                appliedAmount,
                scoreAfter,
                request.getEventCode().name(),
                request.getEventKey(),
                request.getDescription(),
                request.getReferenceType(),
                request.getReferenceId());

        return TeenyScoreChangeResponseDTO.applied(
                request.getChildId(),
                scoreBefore,
                request.getAmount(),
                scoreAfter);
    }

    private int clampScore(int scoreBefore, int requestedAmount) {
        long requestedScore = (long) scoreBefore + requestedAmount;
        return (int) Math.max(
                MIN_SCORE,
                Math.min(MAX_SCORE, requestedScore));
    }
}
