package com.teenyfin.teenymoney.domain.charge.toss;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

// 토스 응답의 card.issuerCode(2자리 코드값)를 한글 카드사명으로 바꿔주는 매핑표.
@Getter
@RequiredArgsConstructor
public enum CardIssuerCode {
    IBK_BC("3K", "기업비씨"),
    GWANGJUBANK("46", "광주"),
    LOTTE("71", "롯데"),
    KDBBANK("30", "산업"),
    BC("31", "BC카드"),
    SAMSUNG("51", "삼성"),
    SAEMAUL("38", "새마을"),
    SHINHAN("41", "신한"),
    SHINHYEOP("62", "신협"),
    CITI("36", "씨티"),
    WOORI_BC("33", "우리(BC매입)"),
    WOORI("W1", "우리"),
    POST("37", "우체국"),
    SAVINGBANK("39", "저축"),
    JEONBUKBANK("35", "전북"),
    JEJUBANK("42", "제주"),
    KAKAOBANK("15", "카카오뱅크"),
    KBANK("3A", "케이뱅크"),
    TOSSBANK("24", "토스뱅크"),
    HANA("21", "하나"),
    HYUNDAI("61", "현대"),
    KOOKMIN("11", "국민"),
    NONGHYEOP("91", "농협"),
    SUHYEOP("34", "수협");

    private final String code;
    private final String koreanName;

    // enum은 선언 시점에 딱 한 번만 만들어지니까, "코드값 -> enum 상수" 조회용 Map을
    // static 블록에서 미리 한 번 만들어두면 나중에 매번 반복문으로 찾을 필요 없이 즉시 조회 가능.
    private static final Map<String, CardIssuerCode> BY_CODE = new HashMap<>();

    static {
        for (CardIssuerCode issuer : values()) {
            BY_CODE.put(issuer.code, issuer);
        }
    }

    // 코드값으로 한글 카드사명을 조회한다. 매핑표에 없는 코드(토스가 나중에 새 카드사를
    // 추가하는 경우 등)가 오면, 등록 자체가 실패하면 안 되니까 코드값을 그대로 리턴함
    public static String toKoreanName(String code) {
        CardIssuerCode issuer = BY_CODE.get(code);
        return issuer != null ? issuer.getKoreanName() : code;
    }
}