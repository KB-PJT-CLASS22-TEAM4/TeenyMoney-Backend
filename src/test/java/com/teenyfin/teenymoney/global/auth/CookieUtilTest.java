package com.teenyfin.teenymoney.global.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookieUtilTest {

    private static final long REFRESH_EXPIRATION_MS = 1_209_600_000L;

    @Test
    void addRefreshCookieIncludesRequiredSecurityAttributes() {
        CookieUtil cookieUtil = new CookieUtil(REFRESH_EXPIRATION_MS, true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieUtil.addRefreshCookie(response, "refresh-token");

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(header);
        assertTrue(header.startsWith("refreshToken=refresh-token;"), header);
        assertTrue(header.contains("Path=/api/v1/auth"), header);
        assertTrue(header.contains("Max-Age=1209600"), header);
        assertTrue(header.contains("Secure"), header);
        assertTrue(header.contains("HttpOnly"), header);
        assertTrue(header.contains("SameSite=Strict"), header);
    }

    @Test
    void addRefreshCookieOmitsSecureForLocalHttp() {
        CookieUtil cookieUtil = new CookieUtil(REFRESH_EXPIRATION_MS, false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieUtil.addRefreshCookie(response, "refresh-token");

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(header);
        assertFalse(header.contains("Secure"), header);
    }

    @Test
    void clearRefreshCookieExpiresCookieWithSameScopeAndSecurityAttributes() {
        CookieUtil cookieUtil = new CookieUtil(REFRESH_EXPIRATION_MS, true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieUtil.clearRefreshCookie(response);

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(header);
        assertTrue(header.startsWith("refreshToken=;"), header);
        assertTrue(header.contains("Path=/api/v1/auth"), header);
        assertTrue(header.contains("Max-Age=0"), header);
        assertTrue(header.contains("Secure"), header);
        assertTrue(header.contains("HttpOnly"), header);
        assertTrue(header.contains("SameSite=Strict"), header);
    }

    @Test
    void readRefreshTokenReturnsMatchingCookieValue() {
        CookieUtil cookieUtil = new CookieUtil(REFRESH_EXPIRATION_MS, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("session", "session-value"),
                new Cookie("refreshToken", "stored-refresh-token"));

        String token = cookieUtil.readRefreshToken(request);

        assertEquals("stored-refresh-token", token);
    }

    @Test
    void readRefreshTokenReturnsNullWhenCookieIsMissing() {
        CookieUtil cookieUtil = new CookieUtil(REFRESH_EXPIRATION_MS, false);
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertNull(cookieUtil.readRefreshToken(request));
    }
}
