package com.teenyfin.teenymoney.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 공통 인프라 에러 코드. COMMON_* 와 시큐리티 교차 관심사(AUTH_UNAUTHORIZED/FORBIDDEN)만 둔다.
 *
 * 도메인 업무 코드는 각 도메인 enum(예: AuthErrorCode)에 두고 이 파일은 건드리지 않는다.
 * 여기에 도메인 코드를 몰아넣으면 여러 담당자가 같은 파일을 고쳐 충돌한다.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    // --- 400 ---
    COMMON_INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    COMMON_INVALID_TYPE(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다."),
    COMMON_MALFORMED_JSON(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다."),
    COMMON_MISSING_HEADER(HttpStatus.BAD_REQUEST, "필수 헤더가 없습니다."),

    // --- 401 / 403 (시큐리티 인프라 교차 관심사) ---
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // --- 404 / 405 ---
    COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    COMMON_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),

    // --- 500 / 503 ---
    COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),
    COMMON_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "서비스를 일시적으로 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
