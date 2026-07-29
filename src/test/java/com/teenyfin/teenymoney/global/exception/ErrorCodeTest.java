package com.teenyfin.teenymoney.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorCodeTest {

    @Test
    @DisplayName("CommonErrorCode는 ErrorCode로 다룰 수 있고 getCode()는 name()과 같다")
    void commonErrorCodeIsErrorCode() {
        ErrorCode code = CommonErrorCode.COMMON_NOT_FOUND;

        assertEquals("COMMON_NOT_FOUND", code.getCode());
        assertEquals(404, code.getStatus().value());
        assertEquals("요청한 경로를 찾을 수 없습니다.", code.getMessage());
    }

    @Test
    @DisplayName("보안 인프라 코드(AUTH_UNAUTHORIZED/FORBIDDEN)는 CommonErrorCode에 남아 있다")
    void securityInfraCodesStayInCommon() {
        assertEquals(401, CommonErrorCode.AUTH_UNAUTHORIZED.getStatus().value());
        assertEquals(403, CommonErrorCode.AUTH_FORBIDDEN.getStatus().value());
    }
}
