package com.teenyfin.teenymoney.domain.charge.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 자동결제 승인 성공 시 토스가 돌려주는 Payment 객체 중, 우리가 실제로 쓰는 필드만 뽑음.
// TossBillingKeyResponseDTO랑 같은 이유로 나머지 필드(card, receipt 등)는 무시.
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossPaymentApprovalResponseDTO {

    //이 결제를 식별하는 고유값 - ChargeMapper.markSuccess()로 charge 행에 저장하고 나중에 취소(환불) API에서도 이 값으로 어떤 결제를 취소할지 지정
    private String paymentKey;

    private String orderId;

    //DONE 이면 승인 완료
    private String status;

    private Long totalAmount;
}
