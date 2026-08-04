package com.teenyfin.teenymoney.domain.teenyscore.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TeenyScoreMonthlyHistoryVO {
    private String yearMonth;
    private Integer teenyScore;
}
