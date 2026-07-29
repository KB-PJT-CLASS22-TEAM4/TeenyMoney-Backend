package com.teenyfin.teenymoney.domain.auth.exception;

import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.GlobalExceptionAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @DisplayName("getCode()는 name()과 같다")
    void codeEqualsName() {
        assertEquals("AUTH_TOKEN_INVALID", AuthErrorCode.AUTH_TOKEN_INVALID.getCode());
    }
}
