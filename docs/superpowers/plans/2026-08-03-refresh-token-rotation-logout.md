# Refresh Token Rotation and Logout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh Token을 원자적으로 Rotation하고, 로그아웃 계정의 Refresh Token과 기존 Access Token을 모두 즉시 무효화한다.

**Architecture:** JWT에는 계정별 무작위 `authGeneration`을 넣고 Redis에는 Refresh Token 원문과 현재 generation만 저장한다. 재발급은 Redis Lua compare-and-set으로 한 번만 성공시키며, 로그아웃은 두 Redis 키를 삭제한다.

**Tech Stack:** Java 17, Spring MVC 5.3, Spring Security 5.8, Spring Data Redis 2.7, JJWT 0.12, JUnit 5, Mockito

## Global Constraints

- Access Token 원문은 Redis에 저장하지 않는다.
- 계정당 Refresh Token 하나를 유지한다.
- DB 스키마와 외부 라이브러리를 추가하지 않는다.
- 로그인·재발급·로그아웃은 CSRF 토큰을 검증한다.
- 로그아웃 대상 토큰이 이미 없어도 `200 OK`다.
- Redis 장애로 무효화를 보장할 수 없으면 `503 COMMON_SERVICE_UNAVAILABLE`이다.
- 사용자 변경인 `application.properties`, `docs/FRONTEND_DEV.md`, `docs/ISSUE 진행.md`는 수정하거나 커밋하지 않는다.

---

### Task 1: JWT 인증 세대와 Redis 원자 연산

**Files:**
- Modify: `src/main/java/com/teenyfin/teenymoney/global/security/jwt/JwtProvider.java`
- Modify: `src/main/java/com/teenyfin/teenymoney/global/auth/RefreshTokenStore.java`
- Modify: `src/test/java/com/teenyfin/teenymoney/global/security/jwt/JwtProviderTest.java`
- Modify: `src/test/java/com/teenyfin/teenymoney/global/auth/RefreshTokenStoreTest.java`

**Interfaces:**
- Produces: `createAccessToken(Long memberId, String role, String authGeneration)`
- Produces: `createRefreshToken(Long memberId, String authGeneration)`
- Produces: `String getOrCreateGeneration(Long memberId)`
- Produces: `String findGeneration(Long memberId)`
- Produces: `boolean rotate(Long memberId, String expectedToken, String newToken, String authGeneration)`
- Produces: `void revokeAll(Long memberId)`

- [ ] **Step 1: JWT Claims 실패 테스트 작성**

```java
String generation = "generation-17";
Claims access = provider.parse(provider.createAccessToken(17L, "PARENT", generation));
Claims refresh = provider.parse(provider.createRefreshToken(17L, generation));
assertEquals(generation, access.get(JwtProvider.CLAIM_AUTH_GENERATION, String.class));
assertEquals(generation, refresh.get(JwtProvider.CLAIM_AUTH_GENERATION, String.class));
assertNull(refresh.get(JwtProvider.CLAIM_ROLE));
```

- [ ] **Step 2: JWT 테스트 실패 확인**

Run: `./gradlew test --tests "*JwtProviderTest"`

Expected: 새 Claim과 메서드 시그니처가 없어 컴파일 실패.

- [ ] **Step 3: JwtProvider 최소 구현**

```java
public static final String CLAIM_AUTH_GENERATION = "authGeneration";
```

두 생성 메서드에 `.claim(CLAIM_AUTH_GENERATION, authGeneration)`을 추가하고 기존 호출부는 다음 Task에서 변경한다.

- [ ] **Step 4: Redis 실패 테스트 작성**

```java
assertEquals("generation-17", store.getOrCreateGeneration(17L));
assertTrue(store.rotate(17L, "old", "new", "generation-17"));
store.revokeAll(17L);
verify(redisTemplate).delete(List.of("refresh:17", "auth:generation:17"));
```

- [ ] **Step 5: Redis 테스트 실패 확인**

Run: `./gradlew test --tests "*RefreshTokenStoreTest"`

Expected: generation과 Rotation 메서드가 없어 컴파일 실패.

- [ ] **Step 6: Redis Lua 최소 구현**

`getOrCreateGeneration`은 UUID 생성, `SET NX PX`, 기존 값 `PEXPIRE`를 한 Lua 명령으로 처리한다. `rotate`는 Refresh Token과 generation이 모두 일치할 때만 새 Refresh 저장과 두 TTL 갱신을 한 명령으로 처리한다.

```java
public void revokeAll(Long memberId) {
    redisTemplate.delete(List.of(refreshKey(memberId), generationKey(memberId)));
}
```

- [ ] **Step 7: Task 1 테스트 통과 확인**

Run: `./gradlew test --tests "*JwtProviderTest" --tests "*RefreshTokenStoreTest"`

Expected: PASS.

- [ ] **Step 8: Task 1 커밋**

Commit: `feat(auth): 토큰 인증 세대와 rotation 저장소 추가`

### Task 2: 로그인과 Refresh 재발급 서비스

**Files:**
- Create: `src/main/java/com/teenyfin/teenymoney/domain/auth/service/TokenReissueResult.java`
- Create: `src/main/java/com/teenyfin/teenymoney/domain/auth/dto/response/TokenReissueResponseDTO.java`
- Modify: `src/main/java/com/teenyfin/teenymoney/domain/auth/service/AuthService.java`
- Modify: `src/test/java/com/teenyfin/teenymoney/domain/auth/service/AuthServiceTest.java`

**Interfaces:**
- Consumes: Task 1의 generation/JWT/rotate 메서드
- Produces: `TokenReissueResult reissue(String refreshToken)`

- [ ] **Step 1: 로그인 generation 및 재발급 실패 테스트 작성**

```java
when(refreshTokenStore.getOrCreateGeneration(17L)).thenReturn("generation-17");
when(jwtProvider.createAccessToken(17L, "PARENT", "generation-17")).thenReturn("new-access");
when(jwtProvider.createRefreshToken(17L, "generation-17")).thenReturn("new-refresh");
when(refreshTokenStore.rotate(17L, "old-refresh", "new-refresh", "generation-17")).thenReturn(true);
TokenReissueResult result = authService.reissue("old-refresh");
assertEquals("new-access", result.accessToken());
```

만료·위조·ACCESS 타입·generation 불일치·Redis Refresh 불일치·없는 회원·비활성 회원·Redis 장애를 각각 검증한다.

- [ ] **Step 2: 서비스 테스트 실패 확인**

Run: `./gradlew test --tests "*AuthServiceTest"`

Expected: 결과 타입과 `reissue`가 없어 컴파일 실패.

- [ ] **Step 3: 로그인 generation 적용**

```java
String generation = refreshTokenStore.getOrCreateGeneration(member.getId());
String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole(), generation);
String refreshToken = jwtProvider.createRefreshToken(member.getId(), generation);
```

- [ ] **Step 4: 재발급 최소 구현**

Refresh JWT의 서명·만료·타입·generation과 회원 상태를 검증한 뒤 새 토큰을 만들고 `rotate` 성공 시에만 반환한다. 만료는 `AUTH_TOKEN_EXPIRED`, 그 외 토큰 오류는 `AUTH_TOKEN_INVALID`, Redis 오류는 `COMMON_SERVICE_UNAVAILABLE`로 변환한다.

- [ ] **Step 5: Task 2 테스트 통과 확인**

Run: `./gradlew test --tests "*AuthServiceTest"`

Expected: PASS.

- [ ] **Step 6: Task 2 커밋**

Commit: `feat(auth): refresh token 재발급과 rotation 구현`

### Task 3: 계정 Access 무효화와 로그아웃

**Files:**
- Modify: `src/main/java/com/teenyfin/teenymoney/global/security/jwt/JwtAuthenticationFilter.java`
- Modify: `src/main/java/com/teenyfin/teenymoney/config/SecurityConfig.java`
- Modify: `src/main/java/com/teenyfin/teenymoney/domain/auth/service/AuthService.java`
- Modify: `src/test/java/com/teenyfin/teenymoney/global/security/jwt/JwtAuthenticationFilterTest.java`
- Modify: `src/test/java/com/teenyfin/teenymoney/domain/auth/service/AuthServiceTest.java`

**Interfaces:**
- Produces: `void logout(String accessToken, String refreshToken)`
- Changes: `JwtAuthenticationFilter(JwtProvider, RefreshTokenStore)`

- [ ] **Step 1: 필터와 로그아웃 실패 테스트 작성**

```java
when(refreshTokenStore.findGeneration(17L)).thenReturn("generation-17");
String token = provider.createAccessToken(17L, "PARENT", "generation-17");
```

generation 일치 인증, 없음·불일치 `AUTH_TOKEN_INVALID`, Redis 장애 `503`을 검증한다. 로그아웃은 유효 Access를 우선 사용하고 없으면 Refresh를 사용해 `revokeAll(memberId)`를 호출하는지 검증한다. 토큰 없음·위조는 성공하고 Redis 장애만 실패해야 한다.

- [ ] **Step 2: Task 3 테스트 실패 확인**

Run: `./gradlew test --tests "*JwtAuthenticationFilterTest" --tests "*AuthServiceTest"`

Expected: 새 필터 생성자와 logout 메서드가 없어 컴파일 실패.

- [ ] **Step 3: 필터와 로그아웃 최소 구현**

필터는 JWT 검증 후 Redis generation을 비교한다. Redis 장애는 request의 `AUTH_ERROR_ATTRIBUTE`에 `COMMON_SERVICE_UNAVAILABLE`을 저장해 기존 EntryPoint가 JSON `503`을 쓰게 한다. 로그아웃은 식별된 회원에만 `revokeAll`을 호출한다.

- [ ] **Step 4: Task 3 테스트 통과 확인**

Run: `./gradlew test --tests "*JwtAuthenticationFilterTest" --tests "*AuthServiceTest"`

Expected: PASS.

- [ ] **Step 5: Task 3 커밋**

Commit: `feat(auth): 로그아웃 시 계정 access token 무효화`

### Task 4: 재발급·로그아웃 HTTP와 CSRF

**Files:**
- Create: `src/main/java/com/teenyfin/teenymoney/domain/auth/dto/response/CsrfTokenResponseDTO.java`
- Modify: `src/main/java/com/teenyfin/teenymoney/domain/auth/controller/AuthController.java`
- Modify: `src/main/java/com/teenyfin/teenymoney/global/auth/CookieUtil.java`
- Modify: `src/main/java/com/teenyfin/teenymoney/config/SecurityConfig.java`
- Modify: `src/test/java/com/teenyfin/teenymoney/domain/auth/controller/AuthControllerTest.java`
- Modify: `src/test/java/com/teenyfin/teenymoney/global/auth/CookieUtilTest.java`
- Modify: `src/test/java/com/teenyfin/teenymoney/global/security/JwtSecurityIntegrationTest.java`

**Interfaces:**
- Produces: `GET /api/v1/auth/csrf`
- Produces: `POST /api/v1/auth/reissue`
- Produces: `POST /api/v1/auth/logout`

- [ ] **Step 1: HTTP와 Cookie 실패 테스트 작성**

```java
when(authService.reissue("old-refresh"))
        .thenReturn(new TokenReissueResult("new-access", "new-refresh"));
```

재발급 응답에는 Access만 있고 Refresh는 새 Cookie인지, logout이 Bearer/Cookie를 서비스에 넘기고 Cookie를 만료하는지, Refresh Cookie가 `SameSite=Strict`인지 검증한다.

- [ ] **Step 2: HTTP 테스트 실패 확인**

Run: `./gradlew test --tests "*AuthControllerTest" --tests "*CookieUtilTest"`

Expected: 엔드포인트와 DTO가 없어 실패.

- [ ] **Step 3: HTTP 최소 구현**

Controller에 csrf/reissue/logout을 추가한다. 재발급은 새 Access만 body로 반환하고 새 Refresh를 Cookie로 보낸다. logout은 Authorization에서 선택적으로 Bearer 값을 추출하고 Cookie를 항상 만료한다.

- [ ] **Step 4: CSRF 실패 테스트 작성**

세 POST 경로는 CSRF가 없으면 `403 AUTH_FORBIDDEN`, `csrf()`가 있으면 필터를 통과하고, GET csrf는 인증 없이 성공하는지 검증한다.

- [ ] **Step 5: CSRF 테스트 실패 확인**

Run: `./gradlew test --tests "*JwtSecurityIntegrationTest"`

Expected: 현재 CSRF disabled라 실패.

- [ ] **Step 6: CSRF 최소 구현**

`CookieCsrfTokenRepository.withHttpOnlyFalse()`와 세 POST 경로의 `AntPathRequestMatcher`를 사용한다. CSRF AccessDeniedHandler는 기존 JSON Handler를 재사용하고 csrf 경로를 공개한다.

- [ ] **Step 7: Task 4 테스트 통과 확인**

Run: `./gradlew test --tests "*AuthControllerTest" --tests "*CookieUtilTest" --tests "*JwtSecurityIntegrationTest"`

Expected: PASS.

- [ ] **Step 8: Task 4 커밋**

Commit: `feat(auth): 재발급 로그아웃 API와 csrf 보호 추가`

### Task 5: API 문서와 전체 검증

**Files:**
- Modify: `src/main/resources/openapi/teenymoney-api.yaml`
- Modify: `README.md`

**Interfaces:**
- Documents: CSRF 발급, Refresh Rotation, Logout, 오류와 Cookie 계약

- [ ] **Step 1: OpenAPI와 README 갱신**

세 신규 API의 요청 헤더·Cookie·응답·오류 예시, FE의 csrf/reissue/bootstrap 순서, 계정당 Refresh 하나라는 범위를 기록한다.

- [ ] **Step 2: 관련 테스트 실행**

Run: `./gradlew test --tests "*Auth*" --tests "*Jwt*" --tests "*CookieUtilTest" --tests "*RefreshTokenStoreTest"`

Expected: PASS.

- [ ] **Step 3: 전체 테스트와 WAR 빌드**

Run: `./gradlew clean test war`

Expected: `BUILD SUCCESSFUL`, `build/libs/ROOT.war` 생성.

- [ ] **Step 4: 변경 범위 검사**

Run: `git status --short`, `git diff --check`, `git diff --stat origin/5-feature-psh-refresh-token...HEAD`

Expected: 사용자 소유 변경은 커밋되지 않고 구현·테스트·문서 파일만 포함.

- [ ] **Step 5: 문서 커밋**

Commit: `docs(auth): refresh rotation API 계약 정리`
