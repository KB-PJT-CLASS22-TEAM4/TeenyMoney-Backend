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
    INVALID_PAYMENT_PASSWORD(HttpStatus.BAD_REQUEST, "결제 비밀번호가 올바르지 않습니다."),
    PAYMENT_LOCKED(HttpStatus.FORBIDDEN, "결제 비밀번호 5회 오류로 잠금 상태입니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_JUST_LOCKED(HttpStatus.FORBIDDEN, "결제 비밀번호를 5회 잘못 입력하여 10분간 결제가 제한됩니다."),
    INVALID_QR_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 주문 아이디입니다."),
    BLOCKED_CATEGORY(HttpStatus.BAD_REQUEST, "차단된 카테고리는 결제할 수 없습니다."),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "이미 완료된 결제입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
