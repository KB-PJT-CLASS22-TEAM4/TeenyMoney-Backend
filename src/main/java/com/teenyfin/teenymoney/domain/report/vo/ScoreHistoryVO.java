package com.teenyfin.teenymoney.domain.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 티니점수 이력 한 줄. event_code 가 필요해서 teenyscore 도메인 VO 를 그대로 쓰지 못한다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoreHistoryVO {

    private int amount;
    private String eventCode;
    private String description;
    private java.time.LocalDateTime createdAt;
}
