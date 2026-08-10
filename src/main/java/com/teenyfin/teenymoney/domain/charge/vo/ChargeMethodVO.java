package com.teenyfin.teenymoney.domain.charge.vo;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ChargeMethodVO {

    private Long id;

    // 이 결제수단을 소유한 부모 회원의 id (T_MBR_INFO_M.id를 가리키는 FK)
    private Long parentId;

    // 토스페이먼츠가 발급해준 빌링키. 이 값 하나로 카드번호 없이도 결제를 낼 수 있음.
    // DB 컬럼은 NOT NULL이라 결제수단 row는 항상 빌링키를 갖고 있어야 함.
    private String billingKey;

    // "CARD" 또는 "ACCOUNT". 지금은 카드만 만들 거라 항상 "CARD"로 저장되지만,
    // 나중에 계좌 등록 기능이 추가돼도 이 VO/테이블은 그대로 재사용할 수 있게
    // 처음부터 카드/계좌 공통 컬럼으로 설계돼 있음.
    private String type;

    // 카드일 때만 값이 채워짐 (계좌면 null). 예: "현대"
    private String cardCompany;
    // 카드일 때만 값이 채워짐. 예: "43301234****123*" (토스가 마스킹해서 내려줌)
    private String maskedCardNumber;

    // 계좌일 때만 값이 채워짐 (지금은 항상 null, 계좌 기능은 나중에 추가)
    private String accountBankName;
    private String accountNumber;


    // 주거래 수단 여부. boolean(소문자) 타입이라 Lombok이 getter를 getIsPrimary()가 아니라
    // isPrimary()로 만들어줌 - "is"로 시작하는 boolean 필드는 Lombok이 이름을 안 바꾸고 그대로 씀.
    // 나중에 JSON으로 응답 내려줄 때도 필드명이 isPrimary로 자연스럽게 나감.
    private boolean primary;

    // "ACTIVE" / "INACTIVE" / "EXPIRED". 삭제해도 row를 진짜로 지우는 게 아니라
    // 이 값을 바꿔서 처리할 예정 (감사/이력 추적을 위해 row 자체는 남겨둠).
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
