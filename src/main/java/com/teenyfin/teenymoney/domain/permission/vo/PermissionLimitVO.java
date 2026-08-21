package com.teenyfin.teenymoney.domain.permission.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 활성 부모-자녀 연결의 선택적 오늘만 허용 한도다. */
@Getter
@Setter
@NoArgsConstructor
public class PermissionLimitVO {
    private Integer monthlyPermissionDayLimit;
}
