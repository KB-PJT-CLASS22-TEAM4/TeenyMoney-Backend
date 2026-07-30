# 하위2 Task 5 — 인가 강제 전환과 HTTP 레벨 검증

> 플랜: [`docs/task-notes/jwt-security-pipeline.md`](jwt-security-pipeline.md) — **플랜의 제약을 정정하며 추가된 Task**
> 산출물: `config/ServletConfig.java`(수정), `config/SecurityConfig.java`(수정), `TestProtectedController.java`, `JwtSecurityIntegrationTest.java`, `TokenPrinterTest.java`
> 선행: [Task 4 — SecurityConfig 배선](jwt-security-task4-securityconfig.md)

---

## 1. 왜 이걸 구현했나

### 출발점: 플랜 문서가 이슈 AC와 모순이었다

Task 1~4를 플랜대로 끝냈지만, 이슈의 완료 기준(AC) 11개 중 **9개가 미충족**이었다. 플랜이 이렇게 못 박았기 때문이다.

> **이 이슈는 `authenticated` 강제를 켜지 않는다.** `SecurityConfig`는 `permitAll` 유지(메커니즘만). `@EnableMethodSecurity`도 제외.

그런데 이슈 AC는 그 반대를 요구한다.

- "공개 경로 외 요청은 기본적으로 인증이 필요하다" → `authenticated()` 필요
- "CHILD 토큰으로 부모 전용 테스트 API를 호출하면 403이 반환된다" → `@EnableMethodSecurity` 필요
- "정상 Access Token으로 테스트용 보호 API에 접근할 수 있다" → 테스트용 보호 컨트롤러 필요

**플랜이 틀렸다.** Task 5는 그 제약을 정정하고 AC를 채우는 작업이다.

### 순서를 바꿀 수 없는 이유 — 하위3도 이걸 전제한다

하위3(로그인) 이슈의 AC에 이런 항목이 있다.

> 토큰 없이 내 정보를 조회하면 **401이 반환된다.**

`permitAll`이면 토큰 없이 조회해도 200이다. 즉 **하위3의 AC가 하위2의 `authenticated()`를 전제한다.**

```
하위2  인증을 강제한다 (문을 잠근다)        ← Task 5
하위3  들어오는 방법을 만든다 (열쇠를 준다)   ← 로그인 API
```

거꾸로 하면 하위3의 AC를 검증할 방법이 없다. **하위2가 먼저다.**

### 로그인이 없어도 지금 할 수 있다

"토큰 발급 수단이 없으니 못 켠다"는 것이 플랜의 논리였다. 그런데 **AC 검증에 필요한 건 로그인 API가 아니라 토큰**이다.

```java
new JwtProvider(secret, 1_800_000L, 1_209_600_000L).createAccessToken(17L, "PARENT");
```

Task 1이 이미 토큰을 만들 수 있다. 로그인 API는 *사용자가 이메일/비밀번호로 토큰을 받는 통로*일 뿐이다.

로그인 부재가 실제로 막는 것은 **AC 구현이 아니라 dev 브랜치 머지**다. 구현 시점과 머지 시점을 분리하면 둘 다 만족한다.

> 확인 결과 `origin/4-feature-psh-auth-api`(하위3)와 `origin/5-feature-psh-refresh-token`(하위4)은 **dev 대비 커밋 0개**로 미착수 상태다. 하위2 브랜치도 push되지 않았다. 즉 지금 이 작업은 **팀원에게 아무 영향이 없다.**

---

## 2. 무엇을 만들었나

| 파일 | 변경 | 위치 |
|---|---|---|
| `ServletConfig.java` | `@EnableMethodSecurity` 추가 | `src/main` |
| `SecurityConfig.java` | 화이트리스트 + `authenticated()` | `src/main` |
| `TestProtectedController.java` | 테스트용 보호 컨트롤러 | **`src/test`** |
| `JwtSecurityIntegrationTest.java` | MockMvc HTTP 레벨 검증 9개 | `src/test` |
| `TokenPrinterTest.java` | 수동 확인용 토큰 출력 | `src/test` |

### 화이트리스트

```java
private static final String[] PUBLIC_ENDPOINTS = {
        "/api/v1/auth/signup",    // 회원가입 — 토큰이 있을 수 없다 (하위3)
        "/api/v1/auth/login",     // 로그인 — 토큰을 받으러 오는 곳 (하위3)
        "/api/v1/auth/reissue",   // 재발급 — Access가 만료된 상태로 온다 (하위4)
        "/api/v1/health",         // 헬스체크 — 모니터링이 토큰 없이 호출
        "/api/v1/health/**",
        "/swagger-ui/**",
        "/api-docs/**"
};
```

공통점은 **"토큰을 아직 못 받았거나 받을 수 없는 상태에서 호출해야 하는 곳"**이다. 여기를 잠그면 로그인 자체가 불가능해진다. 하위3/4 경로를 미리 넣어 그 이슈가 컨트롤러만 만들면 바로 붙게 했다.

경로에 `/api/v1` 접두사를 직접 적는 이유는, `ServletConfig`의 `configurePathMatch`가 `@RestController`에 그 접두사를 붙이기 때문이다.

### 인가 규칙

```java
.authorizeHttpRequests(authorize -> authorize
        .requestMatchers(publicMatchers()).permitAll()
        .anyRequest().authenticated());
```

**순서가 중요하다.** 위에서 아래로 평가하므로 화이트리스트가 먼저 와야 한다. `anyRequest()`를 먼저 두면 모든 요청이 거기 걸려 화이트리스트가 무시된다.

### 화이트리스트는 필터를 끄지 않는다

흔한 오해라 명시해 둔다. 화이트리스트는 **인가 규칙**의 예외 목록이고, **필터는 여전히 모든 요청에 실행된다.**

```
요청 ──▶ [필터] ──▶ [인가 규칙] ──▶ 컨트롤러
          누구인지     들여보낼지
          확인          판단
```

| 요청 | 필터 | 인가 규칙 | 결과 |
|---|---|---|---|
| `/auth/login`, 토큰 없음 | 헤더 없음 → 통과 | 화이트리스트 → 허용 | 200 |
| `/members/me`, 토큰 없음 | 헤더 없음 → 통과 | 인증 필요, 비었음 → 거부 | **401** |
| `/members/me`, 유효 토큰 | `SecurityContext` 채움 | 인증됨 → 허용 | 200 |
| `/auth/login`, **만료 토큰** | 메모 남기고 통과 | 화이트리스트 → 허용 | **200** |

네 번째가 Task 2의 "실패해도 체인을 끊지 않는다"가 값을 하는 지점이다. 앱이 저장된 옛 토큰을 모든 요청에 자동으로 붙이는 상황에서, 필터가 만료를 보고 401을 냈다면 **로그인 자체가 불가능해진다.**

### 테스트 컨트롤러를 `src/test`에 둔 이유

AC에 *"테스트 전용 컨트롤러가 운영 코드 및 배포 결과물에 포함되지 않는다"* 가 있다. `src/main`에 두면 `ROOT.war`에 포함되어 **운영 서버에 테스트 엔드포인트가 열린다.**

검증:
```
./gradlew war
unzip -l build/libs/ROOT.war | grep -c 'TestProtectedController|TokenPrinter|JwtSecurityIntegration'
→ 0
```

---

## 3. 테스트가 확인하는 것

`JwtSecurityIntegrationTest` 9개. Task 1~3의 단위 테스트와 성격이 다르다.

| | Task 1~3 단위 테스트 | 이 통합 테스트 |
|---|---|---|
| 대상 | 클래스 하나의 동작 | **필터·인가·진입점·핸들러가 함께** |
| 요청 | 가짜 객체를 직접 만들어 호출 | **MockMvc로 실제 HTTP 요청** |
| 검증 | `SecurityContext`, attribute | **HTTP 상태 코드 + JSON 본문** |

단위 테스트가 모두 통과해도 **배선이 틀리면 여기서만 드러난다** — 필터 위치, 화이트리스트 순서, 핸들러 등록 누락 같은 것들이다.

### 테스트 설정에서 `TestWebConfig`가 `ServletConfig` 역할을 한다

```java
@Configuration
@EnableWebMvc
@EnableMethodSecurity   // 운영에서는 ServletConfig가 담당한다
static class TestWebConfig {
    @Bean TestProtectedController testProtectedController() { ... }
}
```

`@EnableMethodSecurity`를 여기 두는 이유는 운영과 같다 — `@PreAuthorize`를 붙인 컨트롤러가 이 설정에 등록되기 때문이다. `SecurityConfig`에 두면 `@PreAuthorize`가 무시된다.

### `springSecurity()`를 빼먹으면 전부 200이 된다

```java
mockMvc = webAppContextSetup(applicationContext)
        .apply(springSecurity())      // ← 이게 필터체인을 MockMvc에 붙인다
        .build();
```

없으면 필터체인이 요청에 개입하지 않아 **모든 테스트가 200을 받고 조용히 통과한다.** 인가를 검증하는 테스트가 인가를 우회하는 상태가 된다.

### 9개 테스트와 AC 대응

| # | 테스트 | 입력 | 기대 |
|---|---|---|---|
| 1 | 정상 Access + `MemberPrincipal` 주입 | PARENT Access | 200, `memberId:17`, `role:PARENT` |
| 2 | 토큰 없음 | 없음 | 401 `AUTH_UNAUTHORIZED` |
| 3 | 서명 위조 | 다른 키로 서명 | 401 `AUTH_TOKEN_INVALID` |
| 4 | 만료 | 1분 전 만료 | 401 `AUTH_TOKEN_EXPIRED` |
| 5 | Refresh 오용 | Refresh 토큰 | 401 `AUTH_TOKEN_INVALID` |
| 6 | CHILD → 부모 전용 | CHILD Access | **403** `AUTH_FORBIDDEN` |
| 7 | PARENT → 부모 전용 | PARENT Access | 200 |
| 8 | 화이트리스트 공개 | 없음 | 200 |
| 9 | 화이트리스트 + 만료 토큰 | 만료 Access | **200** (메모는 버려진다) |

**1번이 특히 중요하다.** 컨트롤러가 `@AuthenticationPrincipal MemberPrincipal`로 받은 값을 응답에 실으므로, 필터가 담은 값이 **컨트롤러까지 도달했음**을 응답 본문으로 증명한다. 필터가 다른 타입을 담았다면 `principal`이 조용히 `null`이 되어 NPE가 난다.

**6번과 7번이 짝이다.** 6번만 있으면 "항상 403"인 버그를 못 잡고, 7번만 있으면 "검사가 아예 안 걸림"을 못 잡는다.

**9번은 4번과 짝이다.** 같은 만료 토큰이 경로에 따라 401과 200으로 갈린다 — 판단 주체가 필터가 아니라 인가 규칙임을 증명한다.

### 실행 결과 (실제 HTTP 응답)

```
✓ 정상 Access Token으로 보호 API에 접근하고 MemberPrincipal이 주입된다
      status  : 200   (기대 200)
      body    : {"success":true,"code":"OK","message":"성공","data":{"memberId":17,"role":"PARENT"}}

✓ 토큰 없이 보호 API를 호출하면 401 AUTH_UNAUTHORIZED JSON을 받는다
      status  : 401   (기대 401)
      body    : {"success":false,"code":"AUTH_UNAUTHORIZED","message":"로그인이 필요합니다.","data":null}

✓ 서명이 잘못된 토큰은 401 AUTH_TOKEN_INVALID를 받는다
      status  : 401
      body    : {"success":false,"code":"AUTH_TOKEN_INVALID","message":"유효하지 않은 인증 정보입니다.","data":null}

✓ 만료된 Access Token은 401 AUTH_TOKEN_EXPIRED를 받는다
      status  : 401
      body    : {"success":false,"code":"AUTH_TOKEN_EXPIRED","message":"인증이 만료되었습니다. 다시 로그인해 주세요.","data":null}

✓ tokenType=REFRESH 토큰으로 보호 API를 호출하면 401 AUTH_TOKEN_INVALID를 받는다
✓ CHILD 토큰으로 부모 전용 API를 호출하면 403 AUTH_FORBIDDEN을 받는다
      status  : 403   (기대 403)
      body    : {"success":false,"code":"AUTH_FORBIDDEN","message":"접근 권한이 없습니다.","data":null}

✓ PARENT 토큰으로 부모 전용 API를 호출하면 성공한다
      body    : {"success":true,"code":"OK","message":"성공","data":"parent-only"}

✓ 화이트리스트 경로는 토큰 없이 접근된다                    status: 200
✓ 화이트리스트 경로는 만료된 토큰이 실려와도 통과한다        status: 200

전체: 31 tests, failures=0, errors=0
```

### `TokenPrinterTest` — 수동 확인용

로그인 API가 없어 토큰을 정상적으로 받을 방법이 없다. 그렇다고 **토큰을 발급해주는 개발용 엔드포인트를 만들면 안 된다** (§4.3). 대신 테스트로 뽑아 쓴다.

```
./gradlew test --tests "*TokenPrinterTest" --rerun-tasks -i
```

PARENT/CHILD/만료/위조/Refresh 5종을 개발 기본 시크릿으로 출력한다. `src/test`에 있어 WAR에 포함되지 않는다.

---

## 4. 설계 판단과 근거

### 4.1 `requestMatchers(String...)`를 쓸 수 없다 — 또 부모/자식 컨텍스트 함정

처음엔 이렇게 썼다.

```java
.requestMatchers(PUBLIC_ENDPOINTS).permitAll()   // String... 오버로드
```

컴파일은 됐는데 **`InfrastructureConfigTest` 3개가 전부 깨졌다.**

```
No bean named 'A Bean named mvcHandlerMappingIntrospector of type HandlerMappingIntrospector
is required to use MvcRequestMatcher. Please ensure Spring Security & Spring MVC are
configured in a shared ApplicationContext.'
```

`requestMatchers(String...)`는 Spring MVC가 클래스패스에 있으면 `MvcRequestMatcher`를 만들려 하고, 그건 `@EnableWebMvc`가 등록하는 `mvcHandlerMappingIntrospector` 빈을 요구한다. 그 빈은 **`ServletConfig`(자식 컨텍스트)** 에 있고 `SecurityConfig`는 **루트 컨텍스트**라 볼 수 없다.

**이건 테스트만의 문제가 아니다. 운영에서도 앱이 기동하지 않는다.** 통합 테스트는 `TestWebConfig`에 `@EnableWebMvc`가 있어 통과했으므로, 단위 테스트가 없었다면 톰캣에 올릴 때까지 몰랐을 버그다.

해결: 매칭 방식을 명시해 MVC 컨텍스트 의존을 없앤다.

```java
private static RequestMatcher[] publicMatchers() {
    return Arrays.stream(PUBLIC_ENDPOINTS)
            .map(pattern -> (RequestMatcher) new AntPathRequestMatcher(pattern))
            .toArray(RequestMatcher[]::new);
}
```

> 이 프로젝트에서 **세 번째** 부모/자식 컨텍스트 함정이다. 앞선 두 개는 `@Component` 대신 `@Bean`(Task 4), `@EnableMethodSecurity`를 `ServletConfig`에(Task 5 §4.2). 공통 패턴은 **"루트에 있는 시큐리티 설정이 자식의 MVC 빈을 필요로 할 때"** 다.

### 4.2 `@EnableMethodSecurity`를 `ServletConfig`에 둔 이유

이 애노테이션은 **자기가 속한 컨텍스트의 빈에만** 프록시를 건다. `@PreAuthorize`를 붙일 컨트롤러·서비스는 `ServletConfig`가 스캔하는 자식 컨텍스트에 있다.

```
루트 (SecurityConfig)     ← 여기 넣으면 컨트롤러에 @PreAuthorize가 안 걸린다
자식 (ServletConfig)      ← 컨트롤러가 여기 있다. 여기 넣어야 한다
```

루트에 넣으면 애노테이션은 붙어 있는데 **권한 검사가 조용히 통째로 건너뛰어진다.** CHILD가 부모 전용 API를 호출해도 200이 나온다. 예외도 로그도 없다.

통합 테스트 6번(CHILD → 403)이 이 실수를 잡는다.

### 4.3 개발용 토큰 발급 엔드포인트를 만들지 않은 이유

팀원 편의를 위해 이런 걸 만들 수 있었다.

```java
@PostMapping("/api/v1/auth/dev-token")
public ApiResponse<String> devToken(@RequestParam Long memberId, @RequestParam String role) {
    return ApiResponse.ok(jwtProvider.createAccessToken(memberId, role));
}
```

**만들지 않았다.** 이건 **아무나 원하는 `memberId`와 `role`로 유효한 토큰을 받는 백도어**다.

```bash
curl -X POST 'https://<서버>/api/v1/auth/dev-token?memberId=1&role=PARENT'
# → 1번 부모의 유효한 Access Token
```

인증 파이프라인을 다 만들어놓고 정문 옆에 잠기지 않은 문을 내는 것이다. 필터가 토큰을 아무리 꼼꼼히 검증해도, 그 토큰을 누구나 발급받을 수 있으면 의미가 없다.

이 프로젝트는 **아이들 금융 앱**이다. `T_PAY_METHOD_M`에 부모의 토스 빌링키가, `T_WLT_BASE_M`에 잔액이 있다. 임의 계정 토큰이 발급되면 다른 가족의 지갑에서 결제가 가능하다.

그리고 dev 엔드포인트가 운영에 새는 건 흔한 사고다 — 지우기로 하고 잊거나, 프로필 조건을 잘못 걸거나, 배포 스크립트가 다른 설정을 쓴다. 이 프로젝트는 `ROOT.war` 하나를 올리는 구조라 `src/main`에 두면 **그대로 배포물에 들어간다.**

대안이 `TokenPrinterTest`다. 30분마다 토큰을 다시 뽑아야 하는 불편은 있지만 **배포물에 아무것도 안 들어간다.**

### 4.4 화이트리스트에 하위3/4 경로를 미리 넣은 이유

`/api/v1/auth/signup`, `/api/v1/auth/login`, `/api/v1/auth/reissue`는 아직 존재하지 않는 경로다. 그래도 미리 넣었다.

하위3이 로그인 컨트롤러를 만들 때 **`SecurityConfig`를 건드리지 않아도 되게** 하려는 것이다. 인가 규칙 파일을 도메인마다 고치면 충돌이 잦아진다. 그리고 하위3 개발자가 "왜 로그인이 401인가"로 시간을 쓰지 않는다.

존재하지 않는 경로가 화이트리스트에 있어도 부작용은 없다 — 매칭될 요청이 없을 뿐이다.

### 4.5 테스트 컨트롤러 경로에 `/api/v1`을 직접 적은 이유

운영에서는 `ServletConfig.configurePathMatch`가 `@RestController`에 `/api/v1` 접두사를 자동으로 붙인다. 그런데 테스트의 `TestWebConfig`는 그 규칙을 쓰지 않는다.

두 가지 선택이 있었다.
- `TestWebConfig`에도 `configurePathMatch`를 복제한다 → 설정이 늘고, 테스트 컨트롤러는 배포되지 않으므로 접두사 규칙을 재현할 실익이 없다
- **컨트롤러에 전체 경로를 직접 적는다** (선택) → 화이트리스트 문자열과 테스트 경로가 눈으로 대조된다

후자를 골랐다. 다만 `/api/v1/auth/login` 스텁이 하위3의 실제 로그인 컨트롤러와 경로가 겹칠 수 있는데, 테스트 컨텍스트는 그 컨트롤러를 로딩하지 않으므로 충돌하지 않는다.

---

## 5. 이 Task가 만들지 않은 것

| 항목 | 어디서 |
|---|---|
| 로그인·회원가입 API (토큰 발급) | 하위3 |
| `/api/v1/members/me` | 하위3 |
| Refresh Token 저장·재발급·회전 | 하위3/하위4 |
| `CookieUtil`·`cookie.secure` | 하위3 |
| 실제 도메인 API에 `@PreAuthorize` 적용 | 각 도메인 이슈 |

### 하위2 AC 11개 대조

| AC | 상태 | 검증 |
|---|---|---|
| 정상 Access Token으로 보호 API 접근 | 완료 | 통합 1 |
| 토큰 없는 요청 → 401 JSON | 완료 | 통합 2 |
| 서명 잘못된 토큰 → 401 | 완료 | 통합 3 |
| 만료된 Access → 401 | 완료 | 통합 4 |
| `tokenType=REFRESH`로 호출 시 거부 | 완료 | 통합 5, 필터 단위 4 |
| 컨트롤러에서 `MemberPrincipal` 주입 | 완료 | 통합 1 (응답 본문으로 증명) |
| CHILD → 403 | 완료 | 통합 6 |
| PARENT → 성공 | 완료 | 통합 7 |
| 공개 경로 외 인증 필요 | 완료 | 통합 2, 8 |
| 테스트 컨트롤러가 배포물 미포함 | 완료 | `unzip -l ROOT.war` → 0건 |
| 관련 테스트 통과 | 완료 | 31개 |

## 머지 시 주의

**이 브랜치를 dev에 머지하면 토큰 없는 모든 API 요청이 401이 된다.** 로그인 API가 없으므로 다른 도메인 개발자는 토큰을 정상적으로 얻을 수 없다.

확인 결과 하위3(`origin/4-feature-psh-auth-api`)과 하위4(`origin/5-feature-psh-refresh-token`)는 **dev 대비 커밋 0개로 미착수** 상태다. 따라서 두 가지 중 하나를 택해야 한다.

1. **하위3을 먼저 머지한다** — 로그인이 있으면 정상적으로 토큰을 얻을 수 있다. 가장 깔끔하다
2. **PR에 안내를 남기고 머지한다** — 다른 개발자가 `TokenPrinterTest`로 토큰을 뽑아 쓰도록 알린다

브랜치명이 모두 동일 담당자(`psh`)이므로 하위2 → 하위3을 연달아 올리면 조율 문제가 없다.
