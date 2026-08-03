package com.teenyfin.teenymoney.global.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;

// Refresh Token 생성, 삭제, 조회 및 쿠키 관련 기능 모음
@Component
public class CookieUtil {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String AUTH_PATH = "/api/v1/auth";
    // 쿠키의 CSRF 방어 관련 정책
    // 외부사이트 요청에는 쿠키 전송 제한, 같은 사이트 요청은 사용 가능
    private static final String SAME_SITE = "Strict";

    private final long refreshExpirationMs;
    // 로컬HTTP은 false, 배포HTTPS는 true
    private final boolean secure;

    public CookieUtil(
            @Value("${jwt.refresh-expiration}") long refreshExpirationMs,
            @Value("${cookie.secure}") boolean secure) {
        this.refreshExpirationMs = refreshExpirationMs;
        this.secure = secure;
    }

    // Refresh Token 쿠키를 응답에 추가하는 공개 메서드
    public void addRefreshCookie(HttpServletResponse response, String token) {
        addSetCookieHeader(
                response,
                token,
                Duration.ofSeconds(refreshExpirationMs / 1000));
    }

    // 만료된 쿠키를 보내 브라우저의 쿠키 삭제유도
    public void clearRefreshCookie(HttpServletResponse response) {
        addSetCookieHeader(response, "", Duration.ZERO);
    }

    // request에서 Refresh Token 쿠키를 찾아 반환
    public String readRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    // 발급, 삭제(value="")에서 공통 사용하는 내부 메서드
    private void addSetCookieHeader(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie
                .from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true) // JS에서 쿠키를 읽지 못하게함
                .secure(secure)
                .sameSite(SAME_SITE)
                .path(AUTH_PATH)
                .maxAge(maxAge)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
