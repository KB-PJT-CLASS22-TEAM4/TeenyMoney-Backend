package com.teenyfin.teenymoney.global.security.jwt;

import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "b5ZbpsxM9qd3XTR09Hm/tTbpmTybNbUp/Qc51yyPmk4="; // Base64(32바이트 키)
    private final JwtProvider provider = new JwtProvider(SECRET, 1_800_000L, 1_209_600_000L);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Access 토큰이면 SecurityContext에 MemberPrincipal과 ROLE_ 권한이 채워진다")
    void validAccessTokenAuthenticates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + provider.createAccessToken(17L, "PARENT"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        MemberPrincipal principal = (MemberPrincipal) auth.getPrincipal();

        show("principal", principal, null);
        show("memberId", principal.memberId(), 17L);
        show("role", principal.role(), "PARENT");
        show("authorities", auth.getAuthorities(), "[ROLE_PARENT]");

        assertEquals(17L, principal.memberId());
        assertEquals("PARENT", principal.role());
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PARENT")));
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증하지 않고 통과한다(익명)")
    void noHeaderPassesAnonymous() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        show("Authorization", request.getHeader("Authorization"), null);
        show("authentication", SecurityContextHolder.getContext().getAuthentication(), null);
        show("authError", request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE), null);
        show("체인 통과", chain.getRequest() != null, true);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("만료된 토큰이면 인증하지 않고 authError=AUTH_TOKEN_EXPIRED를 남긴다")
    void expiredTokenSetsAttribute() throws Exception {
        JwtProvider expired = new JwtProvider(SECRET, -60_000L, -60_000L); // 이미 만료
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + expired.createAccessToken(17L, "CHILD"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        show("authentication", SecurityContextHolder.getContext().getAuthentication(), null);
        show("authError", request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE),
                AuthErrorCode.AUTH_TOKEN_EXPIRED);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(AuthErrorCode.AUTH_TOKEN_EXPIRED,
                request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
    }

    @Test
    @DisplayName("Refresh 토큰을 Authorization으로 보내면 거부하고 authError=AUTH_TOKEN_INVALID를 남긴다")
    void refreshTokenInHeaderRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + provider.createRefreshToken(17L));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        show("보낸 tokenType", "REFRESH", null);
        show("authentication", SecurityContextHolder.getContext().getAuthentication(), null);
        show("authError", request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE),
                AuthErrorCode.AUTH_TOKEN_INVALID);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(AuthErrorCode.AUTH_TOKEN_INVALID,
                request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
    }

    // --- 확인용 출력 ---------------------------------------------------------
    // 통과 여부만이 아니라 '실제로 어떤 값이 나왔는지' 눈으로 확인하기 위한 출력이다.
    // 검증 자체는 함께 있는 assert가 담당하고, 이 출력은 판단에 관여하지 않는다.

    private static void show(String label, Object actual, Object expected) {
        String suffix = (expected == null) ? "" : "   (기대 " + quote(expected) + ")";
        System.out.printf("      %-14s: %s%s%n", label, quote(actual), suffix);
    }

    private static String quote(Object value) {
        return (value instanceof String) ? "\"" + value + "\"" : String.valueOf(value);
    }
}
