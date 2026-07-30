# 하위2: JWT·Spring Security 인증 파이프라인 — 구현 플랜

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Access Token을 검증해 Spring Security 인증 객체(`MemberPrincipal` + `ROLE_*`)를 만드는 인증 파이프라인을 구축한다. 인증 **메커니즘**만 제공하고, 전역 `authenticated` 강제는 켜지 않는다(로그인 이후 단계로 미룸).

**Architecture:** `JwtProvider`가 HS256으로 Access/Refresh를 발급·검증한다. `JwtAuthenticationFilter`가 `Authorization: Bearer` 헤더의 Access 토큰을 파싱해 `SecurityContext`에 인증을 채운다. 토큰이 없으면 익명 통과, 손상/만료면 인증 실패 사유를 request attribute에 남긴다. 인증/인가 예외는 `RestAuthenticationEntryPoint`(401)·`RestAccessDeniedHandler`(403)가 `ApiResponse` JSON으로 직접 응답한다. 보안 빈은 모두 **루트 컨텍스트(`SecurityConfig` @Bean)** 에 둔다.

**Tech Stack:** Java 17, Spring Framework 5.3(javax), Spring Security 5.8, jjwt 0.12.6, Jackson, JUnit 5, spring-test(MockHttpServlet*).

## Global Constraints

- Java 17. Spring 5.3 + Security 5.8 — **javax 사용, jakarta 금지**.
- 보안 빈(JwtProvider/필터/핸들러/PasswordEncoder)은 **루트 컨텍스트에 @Bean**으로 등록한다. `@Component` + 컴포넌트 스캔에 의존하지 않는다(ServletConfig가 `global`을 자식 컨텍스트로 스캔하므로 필터체인에 안 붙는다).
- JWT 클레임: `sub`=memberId, `role`=PARENT|CHILD, `tokenType`=**ACCESS|REFRESH**(대문자). 권한 문자열 `ROLE_{role}`.
- 응답은 공통 `ApiResponse` 형식. 상태 코드는 `ErrorCode.getStatus()`.
- 토큰 값·비밀키를 로그에 남기지 않는다.
- ~~**이 이슈는 `authenticated` 강제를 켜지 않는다.** `SecurityConfig`는 `permitAll` 유지(메커니즘만). `@EnableMethodSecurity`도 제외(향후 `ServletConfig`에 배치).~~
  > **[2026-07-30 정정]** 이 제약은 **틀렸다.** 이슈 AC가 *"공개 경로 외 요청은 기본적으로 인증이 필요하다"*, *"CHILD 토큰으로 부모 전용 테스트 API를 호출하면 403이 반환된다"* 를 요구하므로 `authenticated` 강제와 `@EnableMethodSecurity`가 **이 이슈 범위에 포함된다.** 하위3(로그인) 이슈의 AC *"토큰 없이 내 정보를 조회하면 401이 반환된다"* 도 이 전환을 전제하므로, 순서상 하위2가 먼저 켜야 한다.
  >
  > **정정된 제약**: 공개 경로 화이트리스트 + `anyRequest().authenticated()`를 켠다. `@EnableMethodSecurity`는 **`ServletConfig`(자식 컨텍스트)** 에 둔다 — 루트에 두면 컨트롤러에 `@PreAuthorize`가 걸리지 않는다.
- 설계 근거: `docs/superpowers/specs/2026-07-28-auth-foundation-design.md` §4.2~4.6, §패키지 배치 규칙.
- 패키지 베이스: `com.teenyfin.teenymoney`. 보안 코드 위치: `global/security/**`.

---

## File Structure

- `src/main/resources/application.properties` — jwt.secret/access-expiration/refresh-expiration 추가
- `src/main/java/.../global/security/jwt/JwtProvider.java` — 신규(발급/검증)
- `src/main/java/.../global/security/MemberPrincipal.java` — 신규(인증 주체 record)
- `src/main/java/.../global/security/jwt/JwtAuthenticationFilter.java` — 신규(OncePerRequestFilter)
- `src/main/java/.../global/security/ErrorResponseWriter.java` — 신규(에러 JSON 출력 유틸)
- `src/main/java/.../global/security/RestAuthenticationEntryPoint.java` — 신규(401)
- `src/main/java/.../global/security/RestAccessDeniedHandler.java` — 신규(403)
- `src/main/java/.../config/SecurityConfig.java` — 수정(빈 등록 + 필터체인 배선, permitAll 유지)
- 테스트: `JwtProviderTest`, `JwtAuthenticationFilterTest`, `SecurityHandlersTest`, `InfrastructureConfigTest`(확장)

---

## Task 1: JwtProvider + JWT 프로퍼티

**Files:**
- Create: `src/main/java/com/teenyfin/teenymoney/global/security/jwt/JwtProvider.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/teenyfin/teenymoney/global/security/jwt/JwtProviderTest.java`

**Interfaces:**
- Produces:
  - `JwtProvider(String secret, long accessExpirationMs, long refreshExpirationMs)` — `secret`은 **Base64로 인코딩된 무작위 키**
  - `String createAccessToken(Long memberId, String role)` — 클레임 `role`, `tokenType=ACCESS`
  - `String createRefreshToken(Long memberId)` — 클레임 `tokenType=REFRESH`
  - `io.jsonwebtoken.Claims parse(String token)` — 서명·만료 검증, 실패 시 예외 전파

- [ ] **Step 1: 실패하는 테스트 작성**

Create `src/test/java/com/teenyfin/teenymoney/global/security/jwt/JwtProviderTest.java`:

```java
package com.teenyfin.teenymoney.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtProviderTest {

    private static final String SECRET = "b5ZbpsxM9qd3XTR09Hm/tTbpmTybNbUp/Qc51yyPmk4="; // Base64(32바이트 키)
    private final JwtProvider provider = new JwtProvider(SECRET, 1_800_000L, 1_209_600_000L);

    @Test
    @DisplayName("Access 토큰은 sub/role/tokenType=ACCESS 클레임을 담고 파싱으로 복원된다")
    void accessTokenRoundTrip() {
        String token = provider.createAccessToken(17L, "PARENT");
        Claims claims = provider.parse(token);

        assertEquals("17", claims.getSubject());
        assertEquals("PARENT", claims.get("role", String.class));
        assertEquals("ACCESS", claims.get("tokenType", String.class));
    }

    @Test
    @DisplayName("Refresh 토큰은 tokenType=REFRESH를 담고 role은 없다")
    void refreshTokenClaims() {
        Claims claims = provider.parse(provider.createRefreshToken(17L));

        assertEquals("17", claims.getSubject());
        assertEquals("REFRESH", claims.get("tokenType", String.class));
    }

    @Test
    @DisplayName("만료된 토큰 파싱은 ExpiredJwtException을 던진다")
    void expiredTokenThrows() {
        JwtProvider shortLived = new JwtProvider(SECRET, -1L, -1L); // 이미 만료
        String token = shortLived.createAccessToken(17L, "CHILD");

        assertThrows(ExpiredJwtException.class, () -> shortLived.parse(token));
    }

    @Test
    @DisplayName("다른 키로 서명된(위조) 토큰은 JwtException을 던진다")
    void tamperedTokenThrows() {
        String token = new JwtProvider("nDlRA4wqNsD9UWmGExA1MCPvrWiVob6ewIO9ss319jY=", 1_800_000L, 1L)
                .createAccessToken(17L, "PARENT");

        assertThrows(JwtException.class, () -> provider.parse(token));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.teenyfin.teenymoney.global.security.jwt.JwtProviderTest"`
Expected: 컴파일 실패 — `JwtProvider` 없음.

- [ ] **Step 3: JwtProvider 구현**

Create `src/main/java/com/teenyfin/teenymoney/global/security/jwt/JwtProvider.java`:

```java
package com.teenyfin.teenymoney.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 발급·검증. HS256.
 * secret은 Base64로 인코딩된 무작위 키(디코딩 후 최소 256bit=32바이트).
 * 생성 예: openssl rand -base64 32
 * 만료는 ExpiredJwtException, 서명/형식 오류는 JwtException 계열로 전파한다(호출측이 구분).
 */
public class JwtProvider {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtProvider(String secret, long accessExpirationMs, long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String createAccessToken(Long memberId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpirationMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String createRefreshToken(Long memberId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpirationMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

- [ ] **Step 4: JWT 프로퍼티 추가**

In `src/main/resources/application.properties`, 파일 끝에 추가:

```properties

# JWT
# secret은 Base64로 인코딩된 무작위 키다. 생성: openssl rand -base64 32
# 운영은 JWT_SECRET 환경변수로 반드시 override 한다. 아래 기본값은 로컬/개발 전용(비밀 아님).
jwt.secret=${JWT_SECRET:roc9Ns8gE2EDKDkYXuy/tHxrKZXoeaWHTMb+eN8YeZM=}
jwt.access-expiration=${JWT_ACCESS_EXPIRATION_MS:1800000}
jwt.refresh-expiration=${JWT_REFRESH_EXPIRATION_MS:1209600000}
```

> **결정 지점(친-팀 마찰 완화)**: 개발 기본값을 두어 `JWT_SECRET` 없이도 앱이 기동한다. 엄격 모드를 원하면 `${JWT_SECRET}`(기본값 제거)로 바꾸면 되지만, 그 경우 모든 팀원이 환경변수를 설정해야 앱이 뜬다.

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.teenyfin.teenymoney.global.security.jwt.JwtProviderTest"`
Expected: BUILD SUCCESSFUL (4개 통과).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/teenyfin/teenymoney/global/security/jwt/JwtProvider.java \
        src/main/resources/application.properties \
        src/test/java/com/teenyfin/teenymoney/global/security/jwt/JwtProviderTest.java
git commit -m "feat(auth): JwtProvider 발급/검증 및 JWT 프로퍼티 추가"
```

---

## Task 2: MemberPrincipal + JwtAuthenticationFilter

**Files:**
- Create: `src/main/java/com/teenyfin/teenymoney/global/security/MemberPrincipal.java`
- Create: `src/main/java/com/teenyfin/teenymoney/global/security/jwt/JwtAuthenticationFilter.java`
- Test: `src/test/java/com/teenyfin/teenymoney/global/security/jwt/JwtAuthenticationFilterTest.java`

**Interfaces:**
- Consumes: `JwtProvider`(Task 1), `AuthErrorCode`(하위1의 `domain/auth/exception`).
- Produces:
  - `record MemberPrincipal(Long memberId, String role)`
  - `JwtAuthenticationFilter(JwtProvider jwtProvider)` — `public static final String AUTH_ERROR_ATTRIBUTE = "authError"`

- [ ] **Step 1: 실패하는 테스트 작성**

Create `src/test/java/com/teenyfin/teenymoney/global/security/jwt/JwtAuthenticationFilterTest.java`:

```java
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
        assertEquals(17L, principal.memberId());
        assertEquals("PARENT", principal.role());
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PARENT")));
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증하지 않고 통과한다(익명)")
    void noHeaderPassesAnonymous() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("만료된 토큰이면 인증하지 않고 authError=AUTH_TOKEN_EXPIRED를 남긴다")
    void expiredTokenSetsAttribute() throws Exception {
        JwtProvider expired = new JwtProvider(SECRET, -1L, -1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + expired.createAccessToken(17L, "CHILD"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

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

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(AuthErrorCode.AUTH_TOKEN_INVALID,
                request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.teenyfin.teenymoney.global.security.jwt.JwtAuthenticationFilterTest"`
Expected: 컴파일 실패 — `MemberPrincipal`, `JwtAuthenticationFilter` 없음.

- [ ] **Step 3: MemberPrincipal 구현**

Create `src/main/java/com/teenyfin/teenymoney/global/security/MemberPrincipal.java`:

```java
package com.teenyfin.teenymoney.global.security;

/**
 * 인증 주체. Member 엔티티가 아니라 토큰 클레임에서 만든 값 객체다.
 * 컨트롤러에서 @AuthenticationPrincipal MemberPrincipal 로 주입받는다.
 */
public record MemberPrincipal(Long memberId, String role) {
}
```

- [ ] **Step 4: JwtAuthenticationFilter 구현**

Create `src/main/java/com/teenyfin/teenymoney/global/security/jwt/JwtAuthenticationFilter.java`:

```java
package com.teenyfin.teenymoney.global.security.jwt;

import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer <access> 를 검증해 SecurityContext에 인증을 채운다.
 * - 헤더 없음: 익명 통과(인가 규칙이 401 여부 판단)
 * - 유효 + tokenType=ACCESS: 인증
 * - 그 외(만료/손상/ACCESS 아님): 인증하지 않고 request attribute에 사유 표기 → 진입점이 401 처리
 * 토큰 값과 비밀키는 로그에 남기지 않는다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "authError";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtProvider.parse(token);
            if (!JwtProvider.TOKEN_TYPE_ACCESS.equals(claims.get(JwtProvider.CLAIM_TOKEN_TYPE, String.class))) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthErrorCode.AUTH_TOKEN_INVALID);
            } else {
                Long memberId = Long.valueOf(claims.getSubject());
                String role = claims.get(JwtProvider.CLAIM_ROLE, String.class);
                MemberPrincipal principal = new MemberPrincipal(memberId, role);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException e) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthErrorCode.AUTH_TOKEN_INVALID);
        }

        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "com.teenyfin.teenymoney.global.security.jwt.JwtAuthenticationFilterTest"`
Expected: BUILD SUCCESSFUL (4개 통과).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/teenyfin/teenymoney/global/security/MemberPrincipal.java \
        src/main/java/com/teenyfin/teenymoney/global/security/jwt/JwtAuthenticationFilter.java \
        src/test/java/com/teenyfin/teenymoney/global/security/jwt/JwtAuthenticationFilterTest.java
git commit -m "feat(auth): JwtAuthenticationFilter와 MemberPrincipal 추가"
```

---

## Task 3: 인증/인가 실패 응답 핸들러

**Files:**
- Create: `src/main/java/com/teenyfin/teenymoney/global/security/ErrorResponseWriter.java`
- Create: `src/main/java/com/teenyfin/teenymoney/global/security/RestAuthenticationEntryPoint.java`
- Create: `src/main/java/com/teenyfin/teenymoney/global/security/RestAccessDeniedHandler.java`
- Test: `src/test/java/com/teenyfin/teenymoney/global/security/SecurityHandlersTest.java`

**Interfaces:**
- Consumes: `ApiResponse`, `ErrorCode`, `CommonErrorCode`(하위1), `JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE`(Task 2).
- Produces: `RestAuthenticationEntryPoint`(AuthenticationEntryPoint), `RestAccessDeniedHandler`(AccessDeniedHandler).

- [ ] **Step 1: 실패하는 테스트 작성**

Create `src/test/java/com/teenyfin/teenymoney/global/security/SecurityHandlersTest.java`:

```java
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

        assertEquals(401, response.getStatus());
        String body = response.getContentAsString();
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

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"AUTH_TOKEN_EXPIRED\""));
    }

    @Test
    @DisplayName("인가거부: 403 AUTH_FORBIDDEN JSON을 쓴다")
    void accessDeniedForbidden() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("denied"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"AUTH_FORBIDDEN\""));
    }

    static class StubAuthException extends AuthenticationException {
        StubAuthException() {
            super("unauthorized");
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.teenyfin.teenymoney.global.security.SecurityHandlersTest"`
Expected: 컴파일 실패 — 핸들러 클래스 없음.

- [ ] **Step 3: ErrorResponseWriter 구현**

Create `src/main/java/com/teenyfin/teenymoney/global/security/ErrorResponseWriter.java`:

```java
package com.teenyfin.teenymoney.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teenyfin.teenymoney.global.exception.ErrorCode;
import com.teenyfin.teenymoney.global.response.ApiResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 시큐리티 필터 단계(DispatcherServlet 이전)에서 던져지는 인증/인가 예외는
 * @RestControllerAdvice가 잡지 못한다. 그래서 진입점/핸들러가 직접 JSON을 쓴다.
 */
final class ErrorResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ErrorResponseWriter() {
    }

    static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(ApiResponse.error(errorCode)));
    }
}
```

- [ ] **Step 4: RestAuthenticationEntryPoint 구현**

Create `src/main/java/com/teenyfin/teenymoney/global/security/RestAuthenticationEntryPoint.java`:

```java
package com.teenyfin.teenymoney.global.security;

import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.exception.ErrorCode;
import com.teenyfin.teenymoney.global.security.jwt.JwtAuthenticationFilter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 인증 실패(401). 필터가 남긴 authError가 있으면 그 코드로, 없으면 AUTH_UNAUTHORIZED로 응답한다.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        ErrorCode errorCode = (attribute instanceof ErrorCode)
                ? (ErrorCode) attribute
                : CommonErrorCode.AUTH_UNAUTHORIZED;
        ErrorResponseWriter.write(response, errorCode);
    }
}
```

- [ ] **Step 5: RestAccessDeniedHandler 구현**

Create `src/main/java/com/teenyfin/teenymoney/global/security/RestAccessDeniedHandler.java`:

```java
package com.teenyfin.teenymoney.global.security;

import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 인가 실패(403). 인증은 됐지만 권한이 부족한 경우.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorResponseWriter.write(response, CommonErrorCode.AUTH_FORBIDDEN);
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests "com.teenyfin.teenymoney.global.security.SecurityHandlersTest"`
Expected: BUILD SUCCESSFUL (3개 통과).

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/teenyfin/teenymoney/global/security/ErrorResponseWriter.java \
        src/main/java/com/teenyfin/teenymoney/global/security/RestAuthenticationEntryPoint.java \
        src/main/java/com/teenyfin/teenymoney/global/security/RestAccessDeniedHandler.java \
        src/test/java/com/teenyfin/teenymoney/global/security/SecurityHandlersTest.java
git commit -m "feat(auth): 인증 401/인가 403 실패 응답 핸들러 추가"
```

---

## Task 4: SecurityConfig 배선 (permitAll 유지)

보안 빈을 루트 컨텍스트에 등록하고 필터체인에 배선한다. **`authenticated` 강제는 켜지 않는다**(permitAll 유지). `InfrastructureConfigTest`로 컨텍스트 부팅과 빈 등록을 검증한다.

**Files:**
- Modify: `src/main/java/com/teenyfin/teenymoney/config/SecurityConfig.java`
- Test: `src/test/java/com/teenyfin/teenymoney/config/InfrastructureConfigTest.java` (확장)

**Interfaces:**
- Consumes: `JwtProvider`, `JwtAuthenticationFilter`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`.
- Produces(빈): `jwtProvider`, `jwtAuthenticationFilter`, `restAuthenticationEntryPoint`, `restAccessDeniedHandler`, `passwordEncoder`, `securityFilterChain`.

- [ ] **Step 1: 실패하는 테스트 작성 (빈 등록 검증 확장)**

`src/test/java/com/teenyfin/teenymoney/config/InfrastructureConfigTest.java` 를 수정한다.

먼저 `@TestPropertySource`에 jwt 속성을 추가:

```java
@TestPropertySource(properties = {
        "redis.host=localhost",
        "redis.port=6379",
        "jwt.secret=b5ZbpsxM9qd3XTR09Hm/tTbpmTybNbUp/Qc51yyPmk4=",
        "jwt.access-expiration=1800000",
        "jwt.refresh-expiration=1209600000"
})
```

그리고 아래 테스트 메서드를 클래스에 추가:

```java
    @Test
    void securityBeansAreRegistered() {
        assertNotNull(applicationContext.getBean(
                com.teenyfin.teenymoney.global.security.jwt.JwtProvider.class));
        assertNotNull(applicationContext.getBean(
                com.teenyfin.teenymoney.global.security.jwt.JwtAuthenticationFilter.class));
        assertNotNull(applicationContext.getBean(
                org.springframework.security.crypto.password.PasswordEncoder.class));
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "com.teenyfin.teenymoney.config.InfrastructureConfigTest"`
Expected: 실패 — jwt 빈이 아직 없어 `NoSuchBeanDefinitionException`.

- [ ] **Step 3: SecurityConfig 수정**

Replace the entire contents of `src/main/java/com/teenyfin/teenymoney/config/SecurityConfig.java`:

```java
package com.teenyfin.teenymoney.config;

import com.teenyfin.teenymoney.global.security.RestAccessDeniedHandler;
import com.teenyfin.teenymoney.global.security.RestAuthenticationEntryPoint;
import com.teenyfin.teenymoney.global.security.jwt.JwtAuthenticationFilter;
import com.teenyfin.teenymoney.global.security.jwt.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정 (루트 컨텍스트).
 *
 * 보안 빈은 여기 @Bean으로 등록한다. @Component로 두면 ServletConfig가 자식 컨텍스트로
 * 스캔해 필터체인(루트)에 붙지 않는다.
 *
 * 이 이슈(하위2)는 인증 '메커니즘'만 배선한다. 전역 authenticated 강제와
 * @EnableMethodSecurity는 로그인(하위3) 이후 팀 조율 하에 켠다. 지금은 permitAll 유지.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;
    @Value("${jwt.access-expiration}")
    private long accessExpirationMs;
    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    @Bean
    public JwtProvider jwtProvider() {
        return new JwtProvider(jwtSecret, accessExpirationMs, refreshExpirationMs);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtProvider());
    }

    @Bean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint() {
        return new RestAuthenticationEntryPoint();
    }

    @Bean
    public RestAccessDeniedHandler restAccessDeniedHandler() {
        return new RestAccessDeniedHandler();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restAuthenticationEntryPoint())
                        .accessDeniedHandler(restAccessDeniedHandler()))
                // 하위2는 강제하지 않는다. 로그인(하위3) 이후 authenticated로 전환한다.
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

        return http.build();
    }
}
```

- [ ] **Step 4: 전체 테스트 통과 확인**

Run: `./gradlew clean test`
Expected: BUILD SUCCESSFUL. `InfrastructureConfigTest`의 `securityBeansAreRegistered`·`securityFilterChainIsRegistered` 포함 전체 통과.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/teenyfin/teenymoney/config/SecurityConfig.java \
        src/test/java/com/teenyfin/teenymoney/config/InfrastructureConfigTest.java
git commit -m "feat(auth): SecurityConfig에 JWT 필터·핸들러·PasswordEncoder 배선 (permitAll 유지)"
```

---

## 향후 (이 이슈 범위 밖, 조율 필요)

- **`authenticated` 전환**: 로그인(하위3)이 토큰을 발급할 수 있게 된 뒤, `permitAll` → 공개경로 화이트리스트 + `anyRequest().authenticated()`로 전환. 팀 공지 후 별도 작은 PR.
- **`@EnableMethodSecurity`**: 처음 role 게이팅이 필요한 이슈에서 **`ServletConfig`(자식 컨텍스트)** 에 추가한다(컨트롤러/서비스가 자식 컨텍스트에 있어 루트에 두면 `@PreAuthorize`가 안 걸린다).
- **`CookieUtil`·`RefreshTokenStore`·`cookie.secure`**: 하위3/하위4에서 생성.
- **README/`setenv.sh`**: `authenticated` 전환 시 환경변수 필수화(`JWT_SECRET`)와 함께 갱신.

## 완료 기준 (하위2 이슈 AC 매핑)

- [ ] 유효 Access 토큰으로 `MemberPrincipal`+`ROLE_*` 인증 — Task 2
- [ ] 토큰 없음: 익명 통과 / 만료·손상: 사유 표기 — Task 2
- [ ] Refresh 토큰을 헤더로 보내면 거부 — Task 2
- [ ] 인증 실패 401 JSON, 인가 실패 403 JSON (`ApiResponse` 형식) — Task 3
- [ ] 보안 빈이 루트 컨텍스트에 등록되고 필터체인이 구성됨 — Task 4
- [ ] JWT 발급/검증·`tokenType` 계약 — Task 1
- [ ] 앱이 환경변수 없이도 기동(개발 기본값) — Task 1
- [ ] 전체 빌드/테스트 그린 — Task 4
