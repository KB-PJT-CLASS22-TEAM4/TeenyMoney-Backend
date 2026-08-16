package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 적금 이자를 납입 건별 보유기간으로 계산하기 위한 성공 납입 최소 정보다. */
@Getter
@Setter
@NoArgsConstructor
public class SavingContributionVO {
    private Long paidAmount;
    private LocalDateTime paidAt;
}
