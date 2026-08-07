package com.teenyfin.teenymoney.domain.teenyscore.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class TeenyScoreGradeVO {
    private Long gradeId;
    private String gradeName;
    private Integer minScore;
    private Integer maxScore;
    private BigDecimal bonusRate;
    private BigDecimal loanRate;
    private Integer monthlyOverrideLimit;
    private String color;
}
