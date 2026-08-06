package com.teenyfin.teenymoney.domain.family.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FamilyErrorCode implements ErrorCode {

    FAMILY_LINK_CODE_INVALID(HttpStatus.BAD_REQUEST, "만료되었거나 유효하지 않은 연동 코드입니다."),

    // 재발급이 직전 코드를 죽이므로 발급 순서를 부모별로 직렬화한다. 그 자물쇠에 걸린 요청.
    FAMILY_LINK_CODE_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요."),

    // 헤더는 있는데 값이 비었거나 지나치게 긴 경우. 아예 없는 경우는 COMMON_MISSING_HEADER가 잡는다.
    FAMILY_IDEMPOTENCY_KEY_INVALID(
            HttpStatus.BAD_REQUEST,
            "Idempotency-Key 값이 올바르지 않습니다. 발급 요청마다 고유한 UUID를 보내주세요."),

    FAMILY_ALREADY_LINKED(
            HttpStatus.CONFLICT,
            "이미 가족과 연결된 자녀입니다."
    ),

    FAMILY_LINK_TOO_MANY_ATTEMPTS(
            HttpStatus.TOO_MANY_REQUESTS,
            "연동 코드 입력 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요."
    );

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
