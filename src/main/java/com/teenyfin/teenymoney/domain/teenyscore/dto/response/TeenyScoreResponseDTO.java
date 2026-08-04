package com.teenyfin.teenymoney.domain.teenyscore.dto.response;

import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreVO;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class TeenyScoreResponseDTO {
    private final Long childId;
    private final Integer teenyScore;
    private final Long gradeId;
    private final String gradeName;
    private final Integer minScore;
    private final Integer maxScore;
    private final BigDecimal bonusRate;
    private final String color;

    private TeenyScoreResponseDTO(TeenyScoreVO teenyScore) {
        this.childId = teenyScore.getChildId();
        this.teenyScore = teenyScore.getTeenyScore();
        this.gradeId = teenyScore.getGradeId();
        this.gradeName = teenyScore.getGradeName();
        this.minScore = teenyScore.getMinScore();
        this.maxScore = teenyScore.getMaxScore();
        this.bonusRate = teenyScore.getBonusRate();
        this.color = teenyScore.getColor();
    }

    public static TeenyScoreResponseDTO of(
            TeenyScoreVO teenyScore) {
        return new TeenyScoreResponseDTO(teenyScore);
    }
}
