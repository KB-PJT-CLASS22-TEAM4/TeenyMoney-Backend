package com.teenyfin.teenymoney.domain.permission.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PermissionErrorCode implements ErrorCode {

    INVALID_ROLE(HttpStatus.BAD_REQUEST, "유효하지 않은 역할입니다."),
    INVALID_PERMISSION_ID(HttpStatus.BAD_REQUEST, "유효하지 않은 아이디입니다."),
    MONTHLY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "이번 달 오늘만 허용 요청 가능 일수를 초과했습니다."),
    ONLY_CHILD_CAN_MANAGE_PERMISSION(HttpStatus.FORBIDDEN, "자녀만 오늘만 허용을 요청·수정·삭제할 수 있습니다."),
    ONLY_PARENT_CAN_REVIEW_PERMISSION(HttpStatus.FORBIDDEN, "부모만 오늘만 허용 요청을 승인·거절할 수 있습니다."),
    FORBIDDEN_TO_PROCESS_PERMISSION(HttpStatus.FORBIDDEN, "해당 오늘만 허용 요청에 대한 처리 권한이 없습니다."),
    ONLY_CAN_PROCESS_PERMISSION_CREATED_TODAY(HttpStatus.BAD_REQUEST, "오늘 날짜에 생성한 오늘만 허용 요청만 처리할 수 있습니다."),
    ONLY_CAN_PROCESS_PENDING_PERMISSION(HttpStatus.BAD_REQUEST, "수락 혹은 거절되지 않은 오늘만 허용 요청만 처리할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
