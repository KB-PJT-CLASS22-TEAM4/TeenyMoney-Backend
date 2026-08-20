package com.teenyfin.teenymoney.domain.teenyscore.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 연속 만기·반복 해지 판정을 위해 점수 이력에서 가장 최근 이벤트 한 건만 조회할 때 쓴다.
 * 원 가입 건을 다시 조회하려면 referenceType·referenceId가 필요해 이 둘도 함께 담는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class TeenyScoreEventRecordVO {
    private String eventCode;
    private String referenceType;
    private Long referenceId;
}
