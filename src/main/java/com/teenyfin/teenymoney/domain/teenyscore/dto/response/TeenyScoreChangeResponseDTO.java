package com.teenyfin.teenymoney.domain.teenyscore.dto.response;

import lombok.Getter;

/** 점수 변경 전후 값과 실제 반영 여부를 반환한다. */
@Getter
public class TeenyScoreChangeResponseDTO {

    private final Long childId;
    private final Integer scoreBefore;
    private final Integer requestedAmount;
    private final Integer appliedAmount;
    private final Integer scoreAfter;
    private final boolean applied;

    private TeenyScoreChangeResponseDTO(
            Long childId,
            Integer scoreBefore,
            Integer requestedAmount,
            Integer appliedAmount,
            Integer scoreAfter,
            boolean applied) {
        this.childId = childId;
        this.scoreBefore = scoreBefore;
        this.requestedAmount = requestedAmount;
        this.appliedAmount = appliedAmount;
        this.scoreAfter = scoreAfter;
        this.applied = applied;
    }

    public static TeenyScoreChangeResponseDTO applied(
            Long childId,
            int scoreBefore,
            int requestedAmount,
            int scoreAfter) {
        return new TeenyScoreChangeResponseDTO(
                childId,
                scoreBefore,
                requestedAmount,
                scoreAfter - scoreBefore,
                scoreAfter,
                true);
    }

    /** 이미 처리한 eventKey가 들어온 경우 점수를 변경하지 않은 결과다. */
    public static TeenyScoreChangeResponseDTO duplicate(
            Long childId,
            int currentScore,
            int requestedAmount) {
        return new TeenyScoreChangeResponseDTO(
                childId,
                currentScore,
                requestedAmount,
                0,
                currentScore,
                false);
    }
}
