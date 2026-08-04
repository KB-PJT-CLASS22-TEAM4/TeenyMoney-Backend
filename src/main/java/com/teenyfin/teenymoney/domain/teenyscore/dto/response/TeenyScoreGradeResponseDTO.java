package com.teenyfin.teenymoney.domain.teenyscore.dto.response;

import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class TeenyScoreGradeResponseDTO {

    private final Long gradeId;
    private final String gradeName;
    private final Integer minScore;
    private final Integer maxScore;
    private final BigDecimal bonusRate;
    private final String color;

    private TeenyScoreGradeResponseDTO(
            TeenyScoreGradeVO teenyScoreGradeVO) {
        this.gradeId = teenyScoreGradeVO.getGradeId();
        this.gradeName = teenyScoreGradeVO.getGradeName();
        this.minScore = teenyScoreGradeVO.getMinScore();
        this.maxScore = teenyScoreGradeVO.getMaxScore();
        this.bonusRate = teenyScoreGradeVO.getBonusRate();
        this.color = teenyScoreGradeVO.getColor();
    }

    public static TeenyScoreGradeResponseDTO of(
            TeenyScoreGradeVO teenyScoreGradeVO) {
        return new TeenyScoreGradeResponseDTO(
                teenyScoreGradeVO);
    }
}