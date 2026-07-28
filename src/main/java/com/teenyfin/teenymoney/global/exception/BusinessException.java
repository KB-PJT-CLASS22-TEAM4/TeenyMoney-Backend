package com.teenyfin.teenymoney.global.exception;

import lombok.Getter;

/**
 * 업무 규칙 위반. Service에서 던지면 CommonExceptionAdvice가 받아
 * ErrorCode에 정의된 상태 코드와 메시지로 응답한다.
 *
 *   throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);
 *
 * Controller에서 try-catch 하지 말 것. 그대로 통과시키면 된다.
 *
 *   예시)
 *   Controller
 *     -> Service
 *        -> 업무 조건 검사 실패
 *        -> BusinessException 발생
 *     -> GlobalExceptionAdvice
 *     -> ErrorCode에 맞는 HTTP 응답 반환
 *
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 메시지 대신 상황별 문구를 내려야 할 때 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
