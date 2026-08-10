package com.teenyfin.teenymoney.domain.payment.dto.response;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class PaymentResponseDTO {

    private String merchantName;
    private Long amount;
    private Long balance;
    private CategoryPolicyResponseDTO categoryPolicy;
    private LocalDateTime createdAt;
}
