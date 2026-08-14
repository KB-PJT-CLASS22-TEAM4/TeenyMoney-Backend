package com.teenyfin.teenymoney.domain.payment.vo;

import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicy;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class PaymentVO {

    private Long id;
    private Long walletId;
    private Long categoryId;
    private String orderId;
    private String idempotencyKey;
    private String merchantName;
    private CategoryPolicy appliedPolicy;
    private Long amount;
    private String status;
    private String failureReason;
    private LocalDateTime createdAt;
}
