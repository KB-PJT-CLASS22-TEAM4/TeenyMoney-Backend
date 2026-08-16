package com.teenyfin.teenymoney.domain.allowance.vo;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// T_ALW_SCHEDULE_M 한 행의 모양. Mapper가 이 안에 값을 채워서 주고받는다.
// ChargeMethodVO/TransferVO랑 똑같은 패턴 - Getter/Setter/NoArgsConstructor 붙인 순수 데이터 홀더.
@Getter
@Setter
@NoArgsConstructor

public class AllowanceScheduleVO {

    private Long id;
    private Long parentId;
    private Long childId;
    private Long amount;

    private String cycleType; // "WEEKLY" 또는 "MONTHLY" 둘 중 하나만 들어감 (DB CHECK로도 강제됨)
    private Integer paymentDay; // WEEKLY면 요일(1~7, ISO-8601 기준 1=월요일), MONTHLY면 일자(1~28)

    private LocalDate nextPaymentDate;  // 다음 지급 예정일 - 배치가 이 값으로 오늘 처리할 스케줄을 찾음

    // boolean(소문자) 타입이라 Lombok이 getter를 getIsActive()가 아니라 isActive()로 만들어줌
    // - "is"로 시작 안 하는 필드명이라도, boolean 기본형이면 Lombok이 앞에 "is"를 붙여서 getter를 만듦.
    // (ChargeMethodVO의 private boolean primary; -> isPrimary() 패턴과 동일한 이유)
    private boolean active;

    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}


