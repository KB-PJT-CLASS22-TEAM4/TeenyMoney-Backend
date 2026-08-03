package com.teenyfin.teenymoney.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 발급·검증. HS256.
 *
 * 인증 파이프라인의 맨 아래 계층이다. 위쪽이 모두 여기에 의존한다.
 *   로그인(하위3)          → createAccessToken / createRefreshToken
 *   토큰 재발급(하위4)      → parse 로 Refresh 검증
 *   JwtAuthenticationFilter → parse 로 매 요청 Access 검증
 *
 * secret은 Base64로 인코딩된 무작위 키(디코딩 후 최소 256bit=32바이트).
 * 생성 예: openssl rand -base64 32
 * 만료는 ExpiredJwtException, 서명/형식 오류는 JwtException 계열로 전파한다(호출측이 구분).
 *
 * 스프링 애노테이션이 없는 순수 자바 클래스다. SecurityConfig의 @Bean으로 등록한다.
 * @Component를 붙이면 ServletConfig가 자식 컨텍스트에 빈을 만들어 필터체인(루트)에 안 붙는다.
 */
public class JwtProvider {

    // 클레임 이름과 값을 상수로 고정한다. 호출측(필터)이 문자열을 직접 쓰면
    // 오타가 나도 컴파일되고 claims.get이 null을 반환해 인증이 조용히 전부 실패한다.
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String CLAIM_AUTH_GENERATION = "authGeneration";
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    /**
     * 만료 시간을 필드에 하드코딩하지 않고 주입받는다. 그래서
     *   - 운영은 환경변수로 값을 바꿀 수 있고
     *   - 테스트는 음수를 넣어 '이미 만료된 토큰'을 즉시 만들 수 있다(sleep 불필요)
     */
    public JwtProvider(String secret, long accessExpirationMs, long refreshExpirationMs) {
        // Base64 문자열을 디코딩한 바이트로 HS256 서명 키를 만든다.
        // 사람이 읽는 32자 문자열은 길이 검사만 통과하고 실제 엔트로피가 낮다.
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /**
     * Access Token 발급. 매 요청 Authorization 헤더로 실려 다니므로 수명이 짧다(기본 30분).
     * 클레임: sub=memberId, role, tokenType=ACCESS, iat, exp
     */
    public String createAccessToken(Long memberId, String role) {
        return createAccessToken(memberId, role, "legacy");
    }

    public String createAccessToken(Long memberId, String role, String authGeneration) {
        Date now = new Date();
        return Jwts.builder()
                // JWT 표준에서 sub는 문자열이다. 꺼낼 때 Long.valueOf로 되돌린다.
                .subject(String.valueOf(memberId))
                // role을 담아 권한 판단에 DB 조회가 필요 없게 한다.
                .claim(CLAIM_ROLE, role)
                // Access/Refresh는 같은 키로 서명되므로 서명만으로는 구별할 수 없다.
                // 이 클레임이 둘을 나누는 유일한 근거다(필터가 검사한다).
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_AUTH_GENERATION, authGeneration)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpirationMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Refresh Token 발급. Access가 만료됐을 때 새 Access를 받는 용도만이다(기본 14일).
     * 클레임: sub=memberId, tokenType=REFRESH, iat, exp
     *
     * role을 담지 않는다. 14일짜리 토큰에 권한을 박아두면 역할이 바뀌어도
     * 2주간 옛 권한이 살아 있게 된다. 권한은 짧은 Access에만 담아 재발급마다 갱신한다.
     */
    public String createRefreshToken(Long memberId) {
        return createRefreshToken(memberId, "legacy");
    }

    public String createRefreshToken(Long memberId, String authGeneration) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .claim(CLAIM_AUTH_GENERATION, authGeneration)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpirationMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 토큰 검증 후 클레임 반환. Access/Refresh 둘 다 파싱한다
     * (어느 종류인지 구별하는 건 tokenType을 보는 호출측 책임이다).
     *
     * 실패를 null이나 Optional로 뭉개지 않고 예외를 그대로 전파한다.
     * 만료와 위조는 사용자에게 보여줄 말이 다르고, FE가 재발급 시도 여부를 그 차이로 판단한다.
     *   ExpiredJwtException → AUTH_TOKEN_EXPIRED (재발급 시도 가능)
     *   그 외 JwtException  → AUTH_TOKEN_INVALID (재로그인 필요)
     */
    public Claims parse(String token) {
        return Jwts.parser()
                // 서명 검증. JWT payload는 암호화가 아니라 Base64일 뿐이어서 누구나 고칠 수 있다.
                // 위조를 막는 것은 이 검증뿐이다. 빼먹으면 아무 계정으로든 들어올 수 있다.
                .verifyWith(key)
                .build()
                // parseSignedClaims는 서명과 exp(만료)를 함께 확인한다.
                .parseSignedClaims(token)
                .getPayload();
    }
}
