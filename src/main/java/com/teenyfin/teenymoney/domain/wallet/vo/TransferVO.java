package com.teenyfin.teenymoney.domain.wallet.vo;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;


// T_WLT_TRF_L(송금) 한 행의 모양. Mapper가 이 안에 값을 채워서 주고받는다.
@Getter
@Setter
@NoArgsConstructor
public class TransferVO {

    private Long id;
    private Long fromWalletId;
    private Long toWalletId;
    private String idempotencyKey;
    private Long amount;
    private String type;    // ALLOWANCE/DEPOSIT/SAVING/LOAN/TRANSFER
    private String status;  // PENDING/CHARGING/COMPLETED/FAILED/CANCELLED
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
