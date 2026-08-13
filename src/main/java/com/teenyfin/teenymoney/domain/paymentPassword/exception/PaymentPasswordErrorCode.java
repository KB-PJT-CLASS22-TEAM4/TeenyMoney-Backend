package com.teenyfin.teenymoney.domain.paymentPassword.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentPasswordErrorCode implements ErrorCode {

    NOT_SET_PAYMENT_PASSWORD(HttpStatus.BAD_REQUEST, "결제 비밀번호를 등록해 주세요."),
    ALREADY_SET_PAYMENT_PASSWORD(HttpStatus.BAD_REQUEST, "이미 결제 비밀번호를 등록했습니다."),
    INVALID_PAYMENT_PASSWORD(HttpStatus.BAD_REQUEST, "결제 비밀번호가 올바르지 않습니다."),
    PAYMENT_LOCKED(HttpStatus.FORBIDDEN, "결제 비밀번호 5회 오류로 잠금 상태입니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_JUST_LOCKED(HttpStatus.FORBIDDEN, "결제 비밀번호를 5회 잘못 입력하여 10분간 결제가 제한됩니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
