package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SavingPaymentDueVO {
    private Long enrollmentId;
    private Long childId;
    private Long productWalletId;
    private Long monthlyAmount;
    private Integer installmentNo;
}
