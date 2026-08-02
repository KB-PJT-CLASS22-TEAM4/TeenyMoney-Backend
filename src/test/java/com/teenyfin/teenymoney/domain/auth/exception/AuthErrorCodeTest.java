package com.teenyfin.teenymoney.domain.auth.exception;

import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.GlobalExceptionAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 인터페이스화의 목적 검증: 도메인 enum(AuthErrorCode)을 BusinessException으로 던져도
 * 공통 전역 예외 처리가 상태 코드와 code 문자열을 그대로 응답에 반영한다.
 */
class AuthErrorCodeTest {

    @RestController
    static class StubController {
        @GetMapping("/auth-fail")
        Object fail() {
            throw new BusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("AuthErrorCode도 BusinessException으로 던지면 401 + code가 응답에 반영된다")
    void domainErrorCodeFlowsThroughAdvice() throws Exception {
        var response = mockMvc.perform(get("/auth-fail")).andReturn().getResponse();
        String body = response.getContentAsString();

        assertEquals(401, response.getStatus());
        assertTrue(body.contains("\"success\":false"), body);
        assertTrue(body.contains("\"code\":\"AUTH_TOKEN_EXPIRED\""), body);
    }

    @Test
    @DisplayName("모든 코드의 getCode()는 name()과 같고 메시지가 비어 있지 않다")
    void everyCodeIsWellFormed() {
        for (AuthErrorCode code : AuthErrorCode.values()) {
            assertEquals(code.name(), code.getCode());
            assertFalse(code.getMessage().isBlank(), code.name() + "의 메시지가 비어 있다");
        }
    }

    /**
     * 상태 코드는 FE의 분기를 결정한다. 401이면 재로그인, 403이면 안내, 409면 입력 수정이다.
     * 오타로 바뀌어도 컴파일은 되고 화면만 이상해지므로 값으로 못 박는다.
     */
    @Test
    @DisplayName("인증 업무 코드의 HTTP 상태가 설계와 일치한다")
    void statusMapping() {
        assertEquals(HttpStatus.UNAUTHORIZED, AuthErrorCode.AUTH_TOKEN_EXPIRED.getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, AuthErrorCode.AUTH_TOKEN_INVALID.getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, AuthErrorCode.AUTH_INVALID_CREDENTIALS.getStatus());
        assertEquals(HttpStatus.FORBIDDEN, AuthErrorCode.AUTH_INACTIVE_MEMBER.getStatus());
        assertEquals(HttpStatus.CONFLICT, AuthErrorCode.AUTH_DUPLICATE_EMAIL.getStatus());
        assertEquals(HttpStatus.CONFLICT, AuthErrorCode.AUTH_DUPLICATE_PHONE_NUMBER.getStatus());
    }
}
