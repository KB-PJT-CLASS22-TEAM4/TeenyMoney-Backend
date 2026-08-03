package com.teenyfin.teenymoney.domain.auth.exception;

import com.teenyfin.teenymoney.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 인증 도메인 업무 에러 코드.
 *
 * 시큐리티 인프라 교차 관심사 코드(AUTH_UNAUTHORIZED/FORBIDDEN)는 CommonErrorCode에 있고,
 * 여기에는 인증 업무 오류만 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    // --- 토큰 (JwtAuthenticationFilter가 사용) ---
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증이 만료되었습니다. 다시 로그인해 주세요."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."),

    // --- 로그인 ---
    // 없는 이메일과 비밀번호 불일치가 같은 코드를 쓴다. 구분해서 응답하면
    // "이 이메일은 가입돼 있다"는 정보가 유출된다.
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    // 401이 아니라 403이다. 누구인지는 확인됐고 그 계정이 쓸 수 없는 상태인 것이다.
    // 401로 내리면 FE가 재로그인을 유도해 같은 실패를 반복시킨다.
    AUTH_INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "비활성화된 계정입니다."),

    // --- 회원가입 ---
    // 어느 필드가 중복인지 알려줘야 하므로 이메일과 휴대폰을 나눈다.
    AUTH_VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증번호가 올바르지 않습니다."),
    AUTH_VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호가 만료되었습니다."),
    AUTH_VERIFICATION_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS,
            "인증번호 입력 횟수를 초과했습니다."),
    AUTH_SMS_TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS,
            "인증번호를 다시 요청하기 전에 잠시 기다려 주세요."),
    AUTH_SMS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "현재 문자 인증 서비스를 사용할 수 없습니다."),

    AUTH_DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    AUTH_DUPLICATE_PHONE_NUMBER(HttpStatus.CONFLICT, "이미 가입된 휴대폰 번호입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
