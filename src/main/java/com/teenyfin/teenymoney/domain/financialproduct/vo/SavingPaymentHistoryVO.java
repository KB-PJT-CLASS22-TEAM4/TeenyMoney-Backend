package com.teenyfin.teenymoney.domain.financialproduct.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class SavingPaymentHistoryVO {
    private Integer installmentNo;
    private Long scheduledAmount;
    private Long paidAmount;
    private String status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
