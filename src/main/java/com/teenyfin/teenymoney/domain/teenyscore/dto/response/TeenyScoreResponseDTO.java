package com.teenyfin.teenymoney.domain.teenyscore.dto.response;

import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreVO;
import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty(value = "현재 등급의 대출 이자율(%). 대출 불가 등급은 null")
    private final BigDecimal loanRate;
    @ApiModelProperty(value = "현재 등급의 월간 오늘만 허용 가능 횟수")
    private final Integer monthlyOverrideLimit;
    private final String color;

    private TeenyScoreResponseDTO(TeenyScoreVO teenyScore) {
        this.childId = teenyScore.getChildId();
        this.teenyScore = teenyScore.getTeenyScore();
        this.gradeId = teenyScore.getGradeId();
        this.gradeName = teenyScore.getGradeName();
        this.minScore = teenyScore.getMinScore();
        this.maxScore = teenyScore.getMaxScore();
        this.bonusRate = teenyScore.getBonusRate();
        this.loanRate = teenyScore.getLoanRate();
        this.monthlyOverrideLimit = teenyScore.getMonthlyOverrideLimit();
        this.color = teenyScore.getColor();
    }

    public static TeenyScoreResponseDTO of(
            TeenyScoreVO teenyScore) {
        return new TeenyScoreResponseDTO(teenyScore);
    }
}
