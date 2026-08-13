package com.teenyfin.teenymoney.domain.charge.toss.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

// 토스 "자동결제 승인" API(POST /v1/billing/{billingKey})에 보낼 요청 body.
// TossCardBillingKeyRequestDTO랑 같은 이유로 필드명을 토스 문서 그대로 맞춤,
// 불변 객체(생성자로만 값 채움, setter 없음).

@Getter
@AllArgsConstructor
public class TossBillingApproveRequestDTO {

    // 이 결제 수단을 등록한 부모의 customerKey (승인 요청시 신원 확인용)
    private final String customerKey;

    //충전할 금액
    private final Long amount;

    //서버가 발급해서 charge 행에 저장해둔 주문번호 이 값이 Idempotency-Key 헤더값과 짝을 이뤄서 같은 order+Id 같은 멱등키로 재시도 하면 토스가 원래 결과를 그대로 돌려줌
    private final String orderId;

    //사용자에게 보여줄 주문명 : " ~~ 충전"
    private final String orderName;
}
