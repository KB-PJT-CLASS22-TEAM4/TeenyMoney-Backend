package com.teenyfin.teenymoney.domain.charge.toss.dto;




import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 토스 응답 JSON엔 mId, customerKey, authenticatedAt, card{...} 등 훨씬 많은 필드가 오는데,
// 우리가 T_PAY_METHOD_M에 저장할 때 실제로 필요한 건 이 3개뿐이라 나머지는 그냥 무시함.
// @JsonIgnoreProperties(ignoreUnknown = true): 여기 선언 안 한 나머지 JSON 필드가 와도
// 에러 안 내고 조용히 무시하라는 뜻 - 이게 없으면 Jackson이 "모르는 필드가 왔다"고 예외를 던짐.

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossBillingKeyResponseDTO {

    // 발급된 빌링키 - 이게 이 API 호출의 진짜 목적. T_PAY_METHOD_M.billing_key에 저장할 값.
    private String billingKey;

    private Card card;

    // 중첩 객체는 static 내부 클래스로 정의 - Jackson이 JSON의 "card": {...} 부분을
    // 이 클래스로 그대로 매핑해줌.
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    // JSON 구조 자체가 중첩되어있음 — billingKey는 최상위에 바로 있는데, number/issuerCode는 card라는 객체 안에 또 들어있으므로. 자바 객체로 이걸 표현하려면,
    // 우리도 똑같이 "안에 또 다른 객체"를 만들어야 함
    // static 으로 하는 이유는 Jackson이 JSON을 파싱할 때는 Card 객체를 독립적으로
    // 그냥 new Card()로 만들어서 채워야 하거든요. static을 붙이면 "바깥 클래스랑 상관없이 독립적으로 만들 수 있는 클래스"가 돼서, Jackson이 문제없이 만들 수 있음
    public static class Card {
        // 마스킹된 카드번호 (예: "94364662****800*")
        private String number;

        // 카드사를 나타내는 코드값(예: "11"). CardIssuerCode.toKoreanName()으로 한글 카드사명으로 변환해서 씀.
        private String issuerCode;
    }
}
