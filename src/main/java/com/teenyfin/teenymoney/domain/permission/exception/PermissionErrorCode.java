package com.teenyfin.teenymoney.domain.permission.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PermissionErrorCode implements ErrorCode {

    INVALID_ROLE(HttpStatus.BAD_REQUEST, "유효하지 않은 역할입니다."),
    ONLY_CHILD_CAN_CREATE_PERMISSION(HttpStatus.FORBIDDEN, "자녀만 오늘만 허용을 요청할 수 있습니다."),
    ALREADY_EXIST_TODAY_PERMISSION(HttpStatus.FORBIDDEN, "이미 오늘 날짜에 요청한 오늘만 허용이 있습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
