package com.teenyfin.teenymoney.domain.teenyscore.dto.response;

import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreMonthlyHistoryVO;
import lombok.Getter;

@Getter
public class TeenyScoreMonthlyHistoryResponseDTO {
    private final String yearMonth;
    private final Integer teenyScore;

    private TeenyScoreMonthlyHistoryResponseDTO(
            TeenyScoreMonthlyHistoryVO history) {
        this.yearMonth = history.getYearMonth();
        this.teenyScore = history.getTeenyScore();
    }

    public static TeenyScoreMonthlyHistoryResponseDTO of(
            TeenyScoreMonthlyHistoryVO history) {
        return new TeenyScoreMonthlyHistoryResponseDTO(history);
    }
}
