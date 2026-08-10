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

    // 카드사명 (예: "현대") - 카드 등록이라 항상 값이 채워져 있음.
    private String cardCompany;

    // 마스킹된 카드번호 (예: "43301234****123*") - 토스가 이미 마스킹해서 줌.
    private String cardNumber;
}
