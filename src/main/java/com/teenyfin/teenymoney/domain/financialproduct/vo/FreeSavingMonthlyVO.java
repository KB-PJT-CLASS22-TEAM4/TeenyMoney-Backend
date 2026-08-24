package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class FreeSavingMonthlyVO {
    private Long enrollmentId;
    private Long childId;
    private Long monthlyAmount;
    private Integer paymentDay;
    private LocalDate startDate;
    private LocalDate maturityDate;
}
