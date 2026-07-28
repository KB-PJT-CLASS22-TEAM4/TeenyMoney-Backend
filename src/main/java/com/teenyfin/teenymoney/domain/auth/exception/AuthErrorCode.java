package com.teenyfin.teenymoney.domain.auth.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 인증 도메인 업무 에러 코드.
 *
 * 시큐리티 인프라 교차 관심사 코드(AUTH_UNAUTHORIZED/FORBIDDEN)는 CommonErrorCode에 있고,
 * 여기에는 인증 업무 오류만 둔다. (로그인 이슈에서 AUTH_INVALID_CREDENTIALS 추가 예정)
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증이 만료되었습니다. 다시 로그인해 주세요."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
