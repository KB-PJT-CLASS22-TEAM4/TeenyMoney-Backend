package com.teenyfin.teenymoney.domain.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDTO {

    private String orderId;
    private String idempotencyKey;
    private String password;
}
