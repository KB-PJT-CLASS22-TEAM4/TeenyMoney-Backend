package com.teenyfin.teenymoney.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 에러 코드의 공통 계약.
 *
 * 공통 인프라 코드는 CommonErrorCode에, 도메인 업무 코드는 각 도메인의 enum에 둔다.
 *   예) AuthErrorCode, WalletErrorCode ...
 * 각 구현 enum은 getCode()에서 name()을 반환한다(이름과 코드 문자열이 어긋나지 않게).
 *
 *   throw new BusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
 */
public interface ErrorCode {

    HttpStatus getStatus();

    String getMessage();

    /** 응답의 code 값. 구현 enum은 name()을 반환한다. */
    String getCode();
}
