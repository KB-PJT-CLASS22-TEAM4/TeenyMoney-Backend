package com.teenyfin.teenymoney.domain.report.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 가입 상품이 살아 있던 기간. endDate 가 null 이면 아직 진행 중이다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPeriodVO {

    private LocalDate startDate;
    private LocalDate endDate;
}
