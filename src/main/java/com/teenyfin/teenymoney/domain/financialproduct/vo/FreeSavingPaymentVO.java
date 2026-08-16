package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 자유적금 직접납입 검증과 멱등 응답에 필요한 값만 담는다. */
@Getter
@Setter
@NoArgsConstructor
public class FreeSavingPaymentVO {
    private Long enrollmentId;
    private Long childId;
    private Long productWalletId;
    private String savingsType;
    private String status;
    private Long maxMonthAmount;
    private LocalDate startDate;
    private LocalDate maturityDate;
    private Long transferId;
    private Long paidAmount;
}
