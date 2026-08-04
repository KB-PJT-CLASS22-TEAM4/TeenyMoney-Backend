package com.teenyfin.teenymoney.domain.teenyscore.dto.response;

import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreHistoryVO;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class TeenyScoreHistoryResponseDTO {
    private final Long historyId;
    private final Integer amount;
    private final Integer scoreAfter;
    private final String description;
    private final LocalDateTime createdAt;

    private TeenyScoreHistoryResponseDTO(
            TeenyScoreHistoryVO teenyScoreHistoryVO) {
        this.historyId = teenyScoreHistoryVO.getHistoryId();
        this.amount = teenyScoreHistoryVO.getAmount();
        this.scoreAfter = teenyScoreHistoryVO.getScoreAfter();
        this.description = teenyScoreHistoryVO.getDescription();
        this.createdAt = teenyScoreHistoryVO.getCreatedAt();
    }

    public static TeenyScoreHistoryResponseDTO of(
            TeenyScoreHistoryVO teenyScoreHistoryVO) {
        return new TeenyScoreHistoryResponseDTO(teenyScoreHistoryVO);
    }
}
