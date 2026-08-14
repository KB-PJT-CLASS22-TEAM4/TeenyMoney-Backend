package com.teenyfin.teenymoney.domain.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 퀘스트 활동. 보상은 퀘스트 상태가 아니라 실제 지급 완료 이체로 따로 센다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestSummaryVO {

    private int completedCount;
    private int failedCount;
    private int expiredCount;
    private int declinedCount;
    private int inProgressCount;
}
