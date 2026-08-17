package com.teenyfin.teenymoney.domain.notification.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    INVALID_NOTIFICATION_ID(HttpStatus.FORBIDDEN, "유효하지 않은 알림 아이디입니다."),
    FORBIDDEN_TO_NOTIFICATION(HttpStatus.FORBIDDEN, "해당 알림에 대한 권한이 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
