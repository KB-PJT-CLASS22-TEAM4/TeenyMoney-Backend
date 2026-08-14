package com.teenyfin.teenymoney.domain.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 지금 연체 중인 대출 요약.
 *
 * 다른 집계와 달리 기간을 받지 않는다. 연체는 '그 달에 있었던 일'이 아니라 '지금 확인해야 할
 * 상태'라서, 진행 중인 달의 리포트에만 붙인다. 과거 달에는 아예 조회하지 않으므로
 * 지난 달 리포트에 오늘의 연체가 새어 들어갈 수 없다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanOverdueVO {

    /** 연체 회차 수 */
    private int overdueCount;

    /** 연체 중인 대출들의 남은 원금 합계 */
    private long outstandingPrincipal;
}
