package com.teenyfin.teenymoney.domain.payment.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    QR_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 QR코드입니다."),
    INVALID_MERCHANT_CODE(HttpStatus.BAD_REQUEST, "존재하지 않는 업종 코드입니다."),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "잔액이 부족합니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
