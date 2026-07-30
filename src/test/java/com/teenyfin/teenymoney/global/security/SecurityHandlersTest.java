package com.teenyfin.teenymoney.global.security;

import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.global.security.jwt.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHandlersTest {

    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint();
    private final RestAccessDeniedHandler accessDeniedHandler = new RestAccessDeniedHandler();

    @Test
    @DisplayName("진입점: authError 속성이 없으면 401 AUTH_UNAUTHORIZED JSON을 쓴다")
    void entryPointDefaultUnauthorized() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response, new StubAuthException());

        String body = response.getContentAsString();
        show("authError 속성", null, null);
        show("status", response.getStatus(), 401);
        show("contentType", response.getContentType(), null);
        show("body", body, null);

        assertEquals(401, response.getStatus());
        assertTrue(body.contains("\"success\":false"), body);
        assertTrue(body.contains("\"code\":\"AUTH_UNAUTHORIZED\""), body);
    }

    @Test
    @DisplayName("진입점: authError 속성이 있으면 그 코드로 401 JSON을 쓴다")
    void entryPointUsesAttribute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE, AuthErrorCode.AUTH_TOKEN_EXPIRED);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new StubAuthException());

        String body = response.getContentAsString();
        show("authError 속성", AuthErrorCode.AUTH_TOKEN_EXPIRED, null);
        show("status", response.getStatus(), 401);
        show("body", body, null);

        assertEquals(401, response.getStatus());
        assertTrue(body.contains("\"code\":\"AUTH_TOKEN_EXPIRED\""), body);
    }

    @Test
    @DisplayName("인가거부: 403 AUTH_FORBIDDEN JSON을 쓴다")
    void accessDeniedForbidden() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("denied"));

        String body = response.getContentAsString();
        show("status", response.getStatus(), 403);
        show("body", body, null);

        assertEquals(403, response.getStatus());
        assertTrue(body.contains("\"code\":\"AUTH_FORBIDDEN\""), body);
    }

    static class StubAuthException extends AuthenticationException {
        StubAuthException() {
            super("unauthorized");
        }
    }

    // --- 확인용 출력 ---------------------------------------------------------
    // 통과 여부만이 아니라 '실제로 어떤 응답이 나왔는지' 눈으로 확인하기 위한 출력이다.
    // 검증 자체는 함께 있는 assert가 담당하고, 이 출력은 판단에 관여하지 않는다.

    private static void show(String label, Object actual, Object expected) {
        String suffix = (expected == null) ? "" : "   (기대 " + quote(expected) + ")";
        System.out.printf("      %-14s: %s%s%n", label, quote(actual), suffix);
    }

    private static String quote(Object value) {
        return (value instanceof String) ? "\"" + value + "\"" : String.valueOf(value);
    }
}
