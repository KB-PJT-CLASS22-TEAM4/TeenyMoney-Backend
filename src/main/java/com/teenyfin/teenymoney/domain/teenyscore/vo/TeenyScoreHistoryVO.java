package com.teenyfin.teenymoney.domain.teenyscore.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TeenyScoreHistoryVO {
    private Long historyId;
    private Integer amount;
    private Integer scoreAfter;
    private String description;
    private LocalDateTime createdAt;
}
