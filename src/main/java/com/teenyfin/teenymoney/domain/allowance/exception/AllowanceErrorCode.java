package com.teenyfin.teenymoney.domain.allowance.exception;


import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;


// 정기 용돈 스케줄(T_ALW_SCHEDULE_M) 도메인 전용 에러 코드.
@Getter // status, message 필드의 getter 자동 생성
@RequiredArgsConstructor
public enum AllowanceErrorCode implements ErrorCode{

    // 존재하지 않는 스케줄 id로 조회/수정/삭제 시도
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "정기 용돈 스케줄을 찾을 수 없습니다."),

    // 스케줄은 존재하는데, 로그인한 부모의 소유가 아닐 때 (남의 스케줄 건드리려는 시도)
    SCHEDULE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 정기 용돈 스케줄만 다룰 수 있습니다."),

    // UQ_ALW_SCHEDULE_M_PARENT_CHILD(부모-자녀당 1건) UNIQUE 제약 위반 시
    // - 생성할 때, 또는 수정으로 childId를 바꿨는데 그 자녀한테 이미 스케줄이 있을 때
    SCHEDULE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 해당 자녀에게 등록된 정기 용돈 스케줄이 있습니다."),

    // cycleType(WEEKLY/MONTHLY)이랑 paymentDay 범위가 안 맞을 때
    // (DB의 CK_ALW_SCHEDULE_M_PAYMENT_DAY CHECK 제약과 같은 규칙을 서비스 단에서 먼저 검증)
    INVALID_PAYMENT_DAY(HttpStatus.BAD_REQUEST, "날짜를 잘못 입력 하셨습니다.(일주일: 1~7 , 월단위:1~28)");

    private final HttpStatus status;
    private final String message;
    // ErrorCode 인터페이스가 요구하는 메서드. code 값은 항상 enum 상수 이름 그대로 씀
    @Override
    public String getCode() {
        return name();
    }


}
