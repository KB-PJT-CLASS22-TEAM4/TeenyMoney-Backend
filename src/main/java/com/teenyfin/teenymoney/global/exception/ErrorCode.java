package com.teenyfin.teenymoney.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드. enum 이름이 그대로 응답의 code 값이 된다.
 * (이름과 코드 문자열을 따로 두면 오타로 어긋나므로 name()을 쓴다)
 *
 * 네이밍: 도메인접두사_사유
 *   COMMON_ / AUTH_  : 공통. 이 파일에서만 관리한다.
 *   WALLET_ / PAY_ / QUEST_ / FIN_ / ALW_ / TNY_ / NOTI_ : 도메인 담당자가 추가한다.
 *
 * 도메인 코드를 여기 몰아넣으면 4명이 같은 파일을 계속 고쳐 충돌한다.
 * 도메인별로 별도 enum을 만들고 이 파일은 공통만 유지할 것.
 */
public enum ErrorCode {

    // --- 400 ---
    COMMON_INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    COMMON_INVALID_TYPE(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다."),
    COMMON_MALFORMED_JSON(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다."),

    // --- 401 / 403 ---
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

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    /** 응답의 code 값. enum 이름을 그대로 쓴다. */
    public String getCode() {
        return name();
    }
}
