package com.teenyfin.teenymoney.domain.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 오늘만 허용 요청의 상태별 건수. 승인·거절은 결과 정보일 뿐 평가가 아니다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionSummaryVO {

    private int requestCount;
    private int approvedCount;
    private int rejectedCount;
    private int pendingCount;
    private int expiredCount;
    private int reasonWrittenCount;
}
