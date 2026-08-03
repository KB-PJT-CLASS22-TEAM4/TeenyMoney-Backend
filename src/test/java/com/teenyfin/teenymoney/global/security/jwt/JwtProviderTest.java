package com.teenyfin.teenymoney.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtProviderTest {

    private static final String SECRET = "b5ZbpsxM9qd3XTR09Hm/tTbpmTybNbUp/Qc51yyPmk4="; // Base64(32바이트 키)
    private final JwtProvider provider = new JwtProvider(SECRET, 1_800_000L, 1_209_600_000L);

    @Test
    @DisplayName("Access 토큰은 sub/role/tokenType/authGeneration 클레임을 담는다")
    void accessTokenRoundTrip() {
        String token = provider.createAccessToken(17L, "PARENT", "generation-17");
        Claims claims = provider.parse(token);

        show("token", abbreviate(token), null);
        show("sub", claims.getSubject(), "17");
        show("role", claims.get("role", String.class), "PARENT");
        show("tokenType", claims.get("tokenType", String.class), "ACCESS");
        show("authGeneration", claims.get("authGeneration", String.class), "generation-17");

        assertEquals("17", claims.getSubject());
        assertEquals("PARENT", claims.get("role", String.class));
        assertEquals("ACCESS", claims.get("tokenType", String.class));
        assertEquals("generation-17", claims.get("authGeneration", String.class));
    }

    @Test
    @DisplayName("Refresh 토큰은 tokenType=REFRESH를 담고 role은 없다")
    void refreshTokenClaims() {
        Claims claims = provider.parse(provider.createRefreshToken(17L, "generation-17"));

        show("sub", claims.getSubject(), "17");
        show("tokenType", claims.get("tokenType", String.class), "REFRESH");
        show("authGeneration", claims.get("authGeneration", String.class), "generation-17");
        show("role", claims.get("role", String.class), null); // Refresh에는 role을 담지 않으므로 null이 정상

        assertEquals("17", claims.getSubject());
        assertEquals("REFRESH", claims.get("tokenType", String.class));
        assertEquals("generation-17", claims.get("authGeneration", String.class));
        assertNull(claims.get("role"));
    }

    @Test
    @DisplayName("만료된 토큰 파싱은 ExpiredJwtException을 던진다")
    void expiredTokenThrows() {
        // exp는 초 단위로 직렬화되므로 -1ms는 같은 초로 뭉개져 만료로 안 잡힐 수 있다. 1분 전으로 확실히 넘긴다.
        JwtProvider shortLived = new JwtProvider(SECRET, -60_000L, -60_000L); // 이미 만료
        String token = shortLived.createAccessToken(17L, "CHILD", "generation-17");

        ExpiredJwtException thrown =
                assertThrows(ExpiredJwtException.class, () -> shortLived.parse(token));

        show("exp", thrown.getClaims().getExpiration().toInstant(), null);
        show("던진 예외", thrown.getClass().getSimpleName(), "ExpiredJwtException");
        show("메시지", thrown.getMessage(), null);
    }

    @Test
    @DisplayName("다른 키로 서명된(위조) 토큰은 JwtException을 던진다")
    void tamperedTokenThrows() {
        String token = new JwtProvider("nDlRA4wqNsD9UWmGExA1MCPvrWiVob6ewIO9ss319jY=", 1_800_000L, 1L)
                .createAccessToken(17L, "PARENT", "generation-17");

        JwtException thrown = assertThrows(JwtException.class, () -> provider.parse(token));

        show("위조 token", abbreviate(token), null);
        show("던진 예외", thrown.getClass().getSimpleName(), null);
        show("메시지", thrown.getMessage(), null);
    }

    // --- 확인용 출력 ---------------------------------------------------------
    // 통과 여부만이 아니라 '실제로 어떤 값이 나왔는지' 눈으로 확인하기 위한 출력이다.
    // 검증 자체는 아래/위의 assert가 담당하고, 이 출력은 판단에 관여하지 않는다.

    private static void show(String label, Object actual, Object expected) {
        String suffix = (expected == null) ? "" : "   (기대 " + quote(expected) + ")";
        System.out.printf("      %-10s: %s%s%n", label, quote(actual), suffix);
    }

    private static String quote(Object value) {
        return (value instanceof String) ? "\"" + value + "\"" : String.valueOf(value);
    }

    private static String abbreviate(String token) {
        return token.length() <= 40 ? token : token.substring(0, 40) + "...";
    }
}
