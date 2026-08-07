package com.teenyfin.teenymoney.domain.payment.dto.response;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class PaymentQrResponseDTO {

    private String merchantName;
    private Long amount;
    private Long balance;
    private CategoryPolicyResponseDTO categoryPolicy;
    private Integer totalCount;
    private Long totalAmount;
}
