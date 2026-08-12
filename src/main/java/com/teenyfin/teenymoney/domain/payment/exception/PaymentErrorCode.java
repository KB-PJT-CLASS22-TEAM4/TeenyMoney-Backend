package com.teenyfin.teenymoney.domain.payment.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    INVALID_MERCHANT_CODE(HttpStatus.BAD_REQUEST, "존재하지 않는 업종 코드입니다."),
    EXPIRED_QR_CODE(HttpStatus.BAD_REQUEST, "만료된 QR 코드입니다."),
    INVALID_QR_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 주문 아이디입니다."),
    PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 결제 내역만 조회할 수 있습니다."),
    BLOCKED_CATEGORY(HttpStatus.BAD_REQUEST, "차단된 카테고리는 결제할 수 없습니다."),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "이미 완료된 결제입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
