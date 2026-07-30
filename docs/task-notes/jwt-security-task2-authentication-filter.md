# 하위2 Task 2 — MemberPrincipal과 JwtAuthenticationFilter

> 플랜: [`docs/jwt-security-pipeline.md`](jwt-security-pipeline.md) §Task 2
> 산출물: `global/security/MemberPrincipal.java`, `global/security/jwt/JwtAuthenticationFilter.java`, `global/security/jwt/JwtAuthenticationFilterTest.java`
> 선행: [Task 1 — JwtProvider](jwt-security-task1-jwtprovider.md)

---

## 1. 왜 JwtAuthenticationFilter를 구현해야 했나

### 문제: Task 1을 끝냈는데도 `parse`를 부르는 사람이 없었다

Task 1로 토큰을 만들고 읽을 수 있게 됐지만, **아무도 `parse`를 호출하지 않았다.** 테스트만 부르고 있었다. 요청이 들어와도 서버는 여전히 "누구인지" 모른다.

Spring Security는 **`SecurityContext`에 `Authentication` 객체가 들어 있는지**로 인증 여부를 판단한다. 이걸 보는 곳이 전부 아래에 걸려 있다.

```
@AuthenticationPrincipal MemberPrincipal    ← SecurityContext에서 principal을 꺼낸다
@PreAuthorize("hasRole('PARENT')")          ← SecurityContext의 권한을 본다
anyRequest().authenticated()                ← SecurityContext가 비면 401
```

즉 필요한 건 **"`Authorization` 헤더의 토큰 → `SecurityContext`의 `Authentication`" 변환**이고, 그걸 매 요청 수행하는 주체가 이 Task의 산출물이다.

### 왜 필터인가 — 인터셉터로는 늦는다

Spring MVC에는 `HandlerInterceptor`도 있는데 쓸 수 없다. 실행 순서 때문이다.

```
요청
 ├─ 서블릿 필터 체인                  ← JwtAuthenticationFilter (여기서 인증해야 한다)
 │   └─ Spring Security 인가 판단      ← SecurityContext를 보고 401/403 결정
 ├─ DispatcherServlet
 │   └─ HandlerInterceptor            ← 너무 늦다. 인가가 이미 끝났다
 └─ Controller
```

인가 판단이 필터 체인 안에서 일어나므로, 인증은 **그보다 먼저** 채워져야 한다. 인터셉터에서 채우면 인가 규칙이 이미 "인증 안 됨"으로 결론 낸 뒤다.

### MemberPrincipal이 왜 따로 필요한가

`SecurityContext`에 담을 "누구" 객체가 필요한데, 선택지가 셋이었다.

| 담을 것 | 문제 |
|---|---|
| `Member` 엔티티 | **매 요청 DB 조회**가 생긴다. JWT로 저장소 조회를 없앤 의미가 사라진다 |
| `String memberId` | `role`을 잃는다. 권한 판단을 하려면 또 조회해야 한다 |
| **`MemberPrincipal` (선택)** | 토큰 클레임만으로 만든다. DB 조회 0회, `memberId`와 `role` 모두 보존 |

`MemberPrincipal`은 **Member 엔티티가 아니다.** 토큰에 실려온 값을 담은 값 객체이고, DB에 그 회원이 실제로 존재하는지 확인하지 않는다. 그 확인은 로그인(하위3) 시점에 이미 끝났다는 전제다.

---

## 2. 무엇을 만들었나 — 공개 계약

```java
// global/security/MemberPrincipal.java
public record MemberPrincipal(Long memberId, String role) {
}
```

```java
// global/security/jwt/JwtAuthenticationFilter.java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "authError";

    public JwtAuthenticationFilter(JwtProvider jwtProvider)

    @Override
    protected void doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)
}
```

### 판단은 4갈래다

| 들어온 요청 | 필터 동작 | `SecurityContext` | `authError` 속성 | 체인 |
|---|---|---|---|---|
| `Authorization` 헤더 없음 | 아무것도 안 함 | `null` (익명) | 없음 | **통과** |
| 유효 + `tokenType=ACCESS` | 인증 채움 | `MemberPrincipal` + `ROLE_*` | 없음 | **통과** |
| 만료 | 사유만 기록 | `null` | `AUTH_TOKEN_EXPIRED` | **통과** |
| 위조 / `tokenType≠ACCESS` | 사유만 기록 | `null` | `AUTH_TOKEN_INVALID` | **통과** |

**네 경우 모두 체인을 통과한다.** 이게 이 Task의 핵심 설계 결정이고 §4.1에서 다룬다.

### Task 1의 상수가 여기서 쓰인다

```java
if (!JwtProvider.TOKEN_TYPE_ACCESS.equals(claims.get(JwtProvider.CLAIM_TOKEN_TYPE, String.class))) {
```

Task 1에서 `CLAIM_TOKEN_TYPE`·`TOKEN_TYPE_ACCESS`를 `public static final`로 노출한 이유가 이 줄이다. 여기서 `"tokenType"`이라고 문자열을 직접 쓰면 오타가 나도 컴파일되고, `claims.get`이 `null`을 반환해 **모든 토큰이 "ACCESS가 아님"으로 판정되어 인증이 전부 실패한다.** 예외도 안 난다.

---

## 3. 테스트가 확인하는 것

`JwtAuthenticationFilterTest` 4개. Task 1과 같은 방식으로 *입력* / *기대 결과* / *왜* 로 적는다.

테스트 도구는 `MockHttpServletRequest`·`MockHttpServletResponse`·`MockFilterChain`(spring-test)이다. **톰캣도 Spring 컨텍스트도 띄우지 않는다** — 필터는 서블릿 API만 알면 되므로 순수 단위 테스트가 가능하다.

`@AfterEach`에서 `SecurityContextHolder.clearContext()`를 부르는 이유는, `SecurityContextHolder`가 **ThreadLocal**이어서 같은 스레드로 다음 테스트가 돌면 인증이 새어 들어가기 때문이다. 이걸 빼먹으면 `noHeaderPassesAnonymous`가 앞 테스트의 인증을 보고 실패한다.

### 3.1 `validAccessTokenAuthenticates` — 인증이 실제로 채워지나

**입력** `Authorization: Bearer <유효한 Access 토큰(17, PARENT)>`

**기대 결과**
```
principal      : MemberPrincipal[memberId=17, role=PARENT]
principal.memberId() → 17L
principal.role()     → "PARENT"
authorities          → [ROLE_PARENT] 포함
```

**왜 확인하나** — 이 Task의 존재 이유 그 자체다. 확인 포인트가 셋이다.

1. **`principal`의 타입이 `MemberPrincipal`인가.** 컨트롤러가 `@AuthenticationPrincipal MemberPrincipal principal`로 받을 텐데, 필터가 다른 타입(예: `String`)을 넣었다면 주입이 **조용히 `null`**이 된다. 테스트가 `(MemberPrincipal) auth.getPrincipal()`로 캐스팅해 이걸 못 박는다.
2. **`sub`의 문자열이 `Long`으로 되돌려졌나.** Task 1 §3.1에서 `getSubject()`가 `"17"`(문자열)임을 확인했다. 필터가 `Long.valueOf`로 변환하는데, 이걸 빼먹고 `String`을 그대로 담으면 `memberId`를 쓰는 모든 곳에서 타입 오류가 난다.
3. **권한 문자열에 `ROLE_` 접두사가 붙었나.** `hasRole('PARENT')`가 동작하려면 `ROLE_PARENT`여야 한다 (§4.5).

### 3.2 `noHeaderPassesAnonymous` — 토큰 없는 요청을 막지 않나

**입력** 헤더가 아무것도 없는 요청

**기대 결과**
```
authentication : null
authError      : null
체인 통과       : true
```

**왜 확인하나** — 로그인·회원가입·헬스체크는 **토큰 없이 호출돼야 하는 엔드포인트**다. 필터가 토큰 없는 요청을 막으면 **로그인 자체가 불가능해진다.**

주목할 것은 **`체인 통과 : true`** 검증이다. `MockFilterChain.getRequest()`가 non-null인지를 본다 — 체인이 실제로 호출됐다는 증거다.

이게 없으면 "인증이 안 됐다"와 "요청이 필터에서 끊겼다"를 **구별할 수 없다.** 둘 다 `authentication == null`이지만 의미가 완전히 다르다. 전자는 정상(익명 통과)이고 후자는 버그다.

### 3.3 `expiredTokenSetsAttribute` — 만료 사유를 남기나

**입력** `Authorization: Bearer <1분 전에 만료된 Access 토큰>` (`new JwtProvider(SECRET, -60_000L, -60_000L)`)

**기대 결과**
```
authentication : null
authError      : AUTH_TOKEN_EXPIRED
```

**왜 확인하나** — Task 1 §3.3에서 `parse`가 `ExpiredJwtException`을 던지는 걸 확인했다. 이 테스트는 **필터가 그 예외를 잡아 올바른 에러코드로 번역하는지**를 본다.

`AUTH_TOKEN_EXPIRED`와 `AUTH_TOKEN_INVALID`를 구별해야 하는 이유는 사용자에게 보여줄 말이 다르기 때문이다.

```
AUTH_TOKEN_EXPIRED  "인증이 만료되었습니다. 다시 로그인해 주세요."  → FE는 토큰 재발급을 시도할 수 있다
AUTH_TOKEN_INVALID  "유효하지 않은 인증 정보입니다."              → 재발급해도 소용없다. 재로그인
```

필터가 둘을 뭉개면 FE는 재발급 가능 여부를 판단할 수 없고, 만료마다 사용자를 로그인 화면으로 내보내게 된다.

### 3.4 `refreshTokenInHeaderRejected` — Refresh를 Access로 쓰지 못하나

**입력** `Authorization: Bearer <Refresh 토큰>`

**기대 결과**
```
보낸 tokenType : "REFRESH"
authentication : null
authError      : AUTH_TOKEN_INVALID
```

**왜 확인하나** — **네 테스트 중 보안상 가장 중요하다.**

Refresh 토큰은 Access와 **같은 키로 서명된다.** 그래서 서명 검증은 **통과한다.** `parse`는 예외를 던지지 않고 정상적으로 `Claims`를 돌려준다. 만료도 안 됐다(14일).

즉 이 토큰을 막을 근거는 **`tokenType` 클레임 비교 하나뿐이다.** 그 검사를 빼면:

- 14일짜리 Refresh 토큰으로 모든 API를 호출할 수 있다
- Access를 30분으로 줄인 의미가 **완전히 사라진다**
- 토큰이 탈취됐을 때 피해 창이 30분에서 **14일로 늘어난다**

그리고 이 실수는 **어떤 예외도 발생시키지 않는다.** 조용히 동작하며 보안 경계만 무너진다. Task 1 §4.1에서 `tokenType`을 심어둔 것이 여기서 값을 한다.

### 실행 결과

```
./gradlew test --tests "com.teenyfin.teenymoney.global.security.jwt.*"

JwtAuthenticationFilterTest
  ✓ 유효한 Access 토큰이면 SecurityContext에 MemberPrincipal과 ROLE_ 권한이 채워진다
        principal     : MemberPrincipal[memberId=17, role=PARENT]
        memberId      : 17            (기대 17)
        role          : "PARENT"      (기대 "PARENT")
        authorities   : [ROLE_PARENT] (기대 [ROLE_PARENT])

  ✓ Authorization 헤더가 없으면 인증하지 않고 통과한다(익명)
        authentication: null
        authError     : null
        체인 통과      : true          (기대 true)

  ✓ 만료된 토큰이면 인증하지 않고 authError=AUTH_TOKEN_EXPIRED를 남긴다
        authentication: null
        authError     : AUTH_TOKEN_EXPIRED   (기대 AUTH_TOKEN_EXPIRED)

  ✓ Refresh 토큰을 Authorization으로 보내면 거부하고 authError=AUTH_TOKEN_INVALID를 남긴다
        보낸 tokenType : "REFRESH"
        authentication: null
        authError     : AUTH_TOKEN_INVALID   (기대 AUTH_TOKEN_INVALID)

전체: 17 tests, failures=0, errors=0   (JwtProviderTest 4 + 이 파일 4 + 기존 9)
```

값 출력은 `show(label, actual, expected)` 헬퍼로 찍는다. **검증은 `assert`가 하고 출력은 판단에 관여하지 않는다** — 통과 여부만이 아니라 "실제로 어떤 값이 나와서 통과했는지" 눈으로 확인하기 위한 것이다.

### 4개가 함께 커버하는 것

| 토큰 상태 | 담당 테스트 | 인증 | 사유 기록 |
|---|---|---|---|
| 없음 | 3.2 | 안 함 | 없음 |
| 정상 Access | 3.1 | **함** | 없음 |
| 만료 | 3.3 | 안 함 | `AUTH_TOKEN_EXPIRED` |
| 종류가 틀림 | 3.4 | 안 함 | `AUTH_TOKEN_INVALID` |

§2의 4갈래 표와 정확히 1:1로 대응한다.

---

## 4. 설계 판단과 근거

### 4.1 왜 인증 실패에도 체인을 끊지 않나 — 가장 중요한 결정

토큰이 만료·위조됐을 때 두 가지 방식이 가능하다.

| | 필터가 즉시 401 응답 | **사유만 기록하고 통과 (선택)** |
|---|---|---|
| 필터의 책임 | 식별 + 차단 | **식별만** |
| 401 판단 주체 | 필터 | 인가 규칙 + 진입점 |
| `permitAll` 경로에 만료 토큰 | **401** | 통과 |

**선택 이유 1 — 필터는 "차단"을 판단할 정보가 없다.**
"이 경로가 인증을 요구하는가"는 `SecurityConfig`의 인가 규칙이 아는 정보다. 필터가 401을 내면 **인가 규칙을 무시하고 자기가 정책을 정하는 것**이 된다.

**선택 이유 2 — 공개 경로에 만료 토큰이 오는 건 정상 시나리오다.**
브라우저나 앱이 저장해둔 오래된 토큰을 **자동으로 모든 요청에 붙이는** 경우가 흔하다. 사용자가 2주 뒤 앱을 열어 로그인하려 하면, `POST /api/v1/auth/login` 요청에 만료된 Access 토큰이 실려 온다. 필터가 여기서 401을 내면 **로그인조차 할 수 없다.**

**선택 이유 3 — 이 이슈는 `authenticated`를 켜지 않는다.**
플랜의 Global Constraints가 `permitAll` 유지를 명시한다. 필터가 체인을 끊으면 `permitAll`이 무의미해지고, 로그인(하위3)이 없어 토큰을 발급할 수단이 없는 상태에서 **모든 팀원의 작업이 막힌다.**

**선택 이유 4 — 테스트가 단순해진다.**
필터가 응답을 만들지 않으므로 `MockHttpServletResponse`의 상태·본문을 검증할 필요가 없다. `SecurityContext`와 request attribute 두 가지만 보면 된다. 401 JSON 응답은 Task 3에서 별도로 테스트한다.

> **결과**: 지금(`permitAll` 상태)은 만료 토큰을 보내도 요청이 정상 처리된다. 의도된 동작이다. `authenticated`로 전환하면 같은 요청이 401이 되고, 그때 필터 코드는 **한 줄도 바뀌지 않는다.**

### 4.2 왜 예외가 아니라 request attribute인가

사유를 진입점(Task 3)에 넘기는 방법이 둘이었다.

```java
// (A) 예외를 던진다
throw new JwtAuthenticationException(AuthErrorCode.AUTH_TOKEN_EXPIRED);

// (B) request attribute에 기록한다  ← 선택
request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthErrorCode.AUTH_TOKEN_EXPIRED);
```

**(A)를 쓰면 §4.1이 무너진다.** 필터에서 `AuthenticationException`을 던지면 Spring Security가 잡아 즉시 `AuthenticationEntryPoint`를 호출하고 **체인이 끊긴다.** 인가 규칙에 도달하지 못한다.

**(B)는 "기록하되 판단을 미룬다."** attribute는 요청 스코프에 살아 있으므로, 나중에 인가 규칙이 401을 결정하면 진입점이 이 값을 읽어 **정확한 사유로** 응답할 수 있다. 인가 규칙이 통과시키면 attribute는 아무 일 없이 버려진다.

`AUTH_ERROR_ATTRIBUTE`를 `public static final`로 둔 이유는 진입점이 같은 키로 읽어야 하기 때문이다. 양쪽이 문자열을 각자 쓰면 오타 하나로 **사유가 항상 유실되고 기본 메시지만 나간다.**

### 4.3 왜 `record`인가

`MemberPrincipal`은 값 객체다. `record`가 주는 것:

- `equals`/`hashCode`/`toString` 자동 생성 — 출력이 `MemberPrincipal[memberId=17, role=PARENT]`로 나와 테스트에서 바로 읽힌다
- **불변** — `SecurityContext`에 담긴 principal을 아무도 바꿀 수 없다. 요청 처리 중에 `role`이 변조되는 경로가 없다
- 접근자가 `memberId()`/`role()` — 필드명과 같아 별칭이 생기지 않는다

일반 클래스로 만들면 위 세 가지를 직접 써야 하고, setter를 실수로 넣을 여지가 생긴다.

> 계약이 `record`라서 접근자가 `getMemberId()`가 아니라 **`memberId()`**다. 하위3/4에서 이 이름으로 호출해야 한다.

### 4.4 왜 `OncePerRequestFilter`인가

`Filter`를 직접 구현하지 않고 Spring의 `OncePerRequestFilter`를 상속했다. 이름 그대로 **요청당 정확히 한 번만** 실행을 보장한다.

서블릿 컨테이너는 `forward`·`include`·비동기 dispatch에서 필터 체인을 **다시 태울 수 있다.** 그러면 같은 요청에 대해 토큰을 두 번 파싱하고 `SecurityContext`를 두 번 쓴다. 낭비이기도 하고, 두 번째 실행이 첫 번째 결과를 덮어써 예상 못 한 상태가 될 수 있다.

`doFilterInternal`을 오버라이드하면 이 문제를 신경 쓸 필요가 없다.

### 4.5 왜 권한에 `ROLE_` 접두사를 붙이나

```java
new SimpleGrantedAuthority("ROLE_" + role)   // "ROLE_PARENT"
```

Spring Security의 두 표현식이 다르게 동작한다.

| 표현식 | 실제로 찾는 문자열 |
|---|---|
| `hasRole('PARENT')` | `ROLE_PARENT` — **접두사를 자동으로 붙인다** |
| `hasAuthority('PARENT')` | `PARENT` — 그대로 |

우리는 `@PreAuthorize("hasRole('PARENT')")`를 쓸 예정이므로 `ROLE_PARENT`로 만들어야 한다. 접두사 없이 `PARENT`만 담으면 `hasRole`이 **항상 거짓**이 되어 부모도 403을 받는다.

**토큰 클레임에는 접두사를 넣지 않는다.** `role=PARENT`로 담고 권한 객체를 만들 때만 붙인다. 토큰은 매 요청 헤더로 다니므로 5바이트라도 줄이는 게 낫고, 접두사는 Spring Security의 관례일 뿐 도메인 정보가 아니다.

### 4.6 왜 `UsernamePasswordAuthenticationToken`인가

이름이 어색하다 — JWT 인증인데 username/password가 없다. 그래도 쓰는 이유:

```java
new UsernamePasswordAuthenticationToken(principal, null, authorities)
```

- **3-인자 생성자는 `authenticated=true`로 세팅된다.** 2-인자 생성자는 `false`여서 "아직 검증 안 된 인증 시도"로 취급되고 인가가 실패한다. 이건 Spring Security에서 자주 실수하는 지점이다
- **credentials에 `null`** — 비밀번호가 없고, `SecurityContext`에 비밀번호를 남기지 않는 게 안전하다
- 별도 `JwtAuthenticationToken` 클래스를 만들 수도 있지만, 얻는 게 이름뿐이고 Spring Security 전반에서 이 클래스를 관례적으로 재사용한다

### 4.7 토큰 값을 로그에 남기지 않는다

이 필터에는 로거가 **아예 없다.** 플랜의 Global Constraints("토큰 값·비밀키를 로그에 남기지 않는다")를 지키는 가장 확실한 방법이다.

`catch` 블록에서 `log.warn("invalid token: {}", token)` 같은 코드를 쓰기 쉬운데, 그러면 **유효한 Access 토큰이 로그 파일에 평문으로 쌓인다.** 로그 접근 권한이 있는 사람이 아무 계정이나 가로챌 수 있다. 예외 변수 `e`도 사용하지 않는다.

---

## 5. 이 Task가 만들지 않은 것

| 항목 | 어디서 |
|---|---|
| `authError`를 읽어 **401 JSON 응답** 만들기 | Task 3 `RestAuthenticationEntryPoint` |
| 403 JSON 응답 | Task 3 `RestAccessDeniedHandler` |
| 필터를 **필터체인에 등록**하기 | Task 4 `SecurityConfig` |
| `MemberPrincipal`을 빈으로 주입받는 배선 | 불필요 — 필터가 직접 생성한다 |
| `authenticated` 강제 / `@EnableMethodSecurity` | 하위3 이후, 팀 조율 |
| 회원이 DB에 실제로 존재하는지 확인 | 하위3 로그인 시점 |
| 비활성(`status=INACTIVE`) 회원 토큰 즉시 차단 | 미정 — JWT stateless의 트레이드오프. 필요하면 하위4에서 Redis 블랙리스트 |

**지금 이 필터는 만들어졌지만 아직 동작하지 않는다.** Task 4에서 `SecurityConfig`가 `addFilterBefore`로 체인에 등록해야 실제 요청에 개입한다. 그래서 Task 2의 검증은 단위 테스트뿐이고, 실제 HTTP 요청에서의 동작 확인은 Task 4 이후다.

## 미검증으로 남긴 것

`JwtProviderTest.refreshTokenClaims`의 이름은 "role은 없다"인데 **`role`이 없음을 검증하는 assert가 없다.** 값 출력으로 `role : null`은 확인되지만 테스트가 강제하지는 않는다. 플랜에 없어서 추가하지 않았다. 넣으려면 한 줄이다.

```java
assertNull(claims.get("role", String.class));
```
