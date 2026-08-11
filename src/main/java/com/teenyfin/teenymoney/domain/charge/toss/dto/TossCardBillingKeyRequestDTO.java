package com.teenyfin.teenymoney.domain.charge.toss.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TossCardBillingKeyRequestDTO {

    // 토스 "카드로 빌링키 발급" API(POST /v1/billing/authorizations/card)에 그대로 보낼 요청 body.
    // 필드 이름을 전부 토스 문서에 나온 이름과 똑같이 맞췄음 - RestTemplate이 이 객체를
    // JSON으로 변환할 때 필드명을 그대로 JSON key로 쓰기 때문에(camelCase 그대로),
    // 이름이 다르면 토스가 그 필드를 못 알아봄.
    // @Setter를 안 붙인 이유: 이 객체는 "한 번 만들면 값이 안 바뀌는" 요청 전용 객체라서,
    // 생성자로만 값을 채우고 이후엔 read-only로 쓰는 게 안전함(불변 객체).

    // 우리 회원(부모)을 가리키는 식별자. T_MBR_INFO_M.customer_key에 저장해둔 값을 그대로 씀.
    private final String customerKey;

    // 카드번호 (사용자가 입력한 값 그대로, 최대 20자)
    private final String cardNumber;

    // 카드 유효기간 연도 (예: "27")
    private final String cardExpirationYear;
    // 카드 유효기간 월 (예: "05")
    private final String cardExpirationMonth;

    // 카드 소유자 확인용 - 생년월일 6자리(YYMMDD) 또는 사업자등록번호 10자리
    private final String customerIdentityNumber;

    // 카드 비밀번호 앞 2자리
    private final String cardPassword;
}
