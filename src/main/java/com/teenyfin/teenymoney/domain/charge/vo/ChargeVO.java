package com.teenyfin.teenymoney.domain.charge.vo;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor

public class ChargeVO {

    private Long id;
    private Long walletId;
    private Long paymentMethodId;
    private String idempotencyKey;
    private Long amount;

    // PENDING / PROCESSING / SUCCESS / FAILED
    private String status;
    private String failureReason;

    // 토스에 보낼 때 쓰는 주문번호. createPendingCharge()에서 서버가 UUID로 발급해 저장.
    private String orderId;

    // 토스 승인 성공 후에만 값이 채워짐(그 전까진 null).
    private String paymentKey;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
