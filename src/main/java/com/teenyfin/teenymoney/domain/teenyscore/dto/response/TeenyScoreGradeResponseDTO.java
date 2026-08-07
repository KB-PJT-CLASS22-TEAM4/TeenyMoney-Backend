package com.teenyfin.teenymoney.domain.teenyscore.dto.response;

import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class TeenyScoreGradeResponseDTO {

    private final Long gradeId;
    private final String gradeName;
    private final Integer minScore;
    private final Integer maxScore;
    private final BigDecimal bonusRate;
    @ApiModelProperty(value = "등급별 대출 이자율(%). 대출 불가 등급은 null")
    private final BigDecimal loanRate;
    @ApiModelProperty(value = "등급별 월간 오늘만 허용 가능 횟수")
    private final Integer monthlyOverrideLimit;
    private final String color;

    private TeenyScoreGradeResponseDTO(
            TeenyScoreGradeVO teenyScoreGradeVO) {
        this.gradeId = teenyScoreGradeVO.getGradeId();
        this.gradeName = teenyScoreGradeVO.getGradeName();
        this.minScore = teenyScoreGradeVO.getMinScore();
        this.maxScore = teenyScoreGradeVO.getMaxScore();
        this.bonusRate = teenyScoreGradeVO.getBonusRate();
        this.loanRate = teenyScoreGradeVO.getLoanRate();
        this.monthlyOverrideLimit =
                teenyScoreGradeVO.getMonthlyOverrideLimit();
        this.color = teenyScoreGradeVO.getColor();
    }

    public static TeenyScoreGradeResponseDTO of(
            TeenyScoreGradeVO teenyScoreGradeVO) {
        return new TeenyScoreGradeResponseDTO(
                teenyScoreGradeVO);
    }
}
