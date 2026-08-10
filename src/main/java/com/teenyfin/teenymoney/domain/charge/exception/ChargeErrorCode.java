package com.teenyfin.teenymoney.domain.charge.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;


@Getter
@RequiredArgsConstructor

public enum ChargeErrorCode implements ErrorCode {
    TOSS_BILLING_KEY_ISSUE_FAILED(
            HttpStatus.BAD_GATEWAY, "카드 등록에 실패했습니다."),
    TOSS_BILLING_KEY_DELETE_FAILED(
            HttpStatus.BAD_GATEWAY, "결제수단 삭제에 실패했습니다."),
    TOSS_RESPONSE_INVALID(
            HttpStatus.BAD_GATEWAY, "토스페이먼츠 응답이 올바르지 않습니다."),

    // 아래 2개는 다음 단계(ChargeMethodService)에서 쓸 건데, 파일 하나로 할 수 있다.
    CHARGE_METHOD_NOT_FOUND(
            HttpStatus.NOT_FOUND, "결제수단을 찾을 수 없습니다."),
    CHARGE_METHOD_ACCESS_DENIED(
            HttpStatus.FORBIDDEN, "본인의 결제수단만 관리할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name(); // enum 상수 이름(예: "TOSS_BILLING_KEY_ISSUE_FAILED") 그대로가 응답의 code 값이 됨
    }
}
