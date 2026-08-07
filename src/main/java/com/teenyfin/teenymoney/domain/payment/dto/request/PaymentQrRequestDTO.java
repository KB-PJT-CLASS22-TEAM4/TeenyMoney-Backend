package com.teenyfin.teenymoney.domain.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentQrRequestDTO {

    private String merchantName;
    private String merchantCode;
    private Long amount;
    private LocalDateTime expiredAt;
}
