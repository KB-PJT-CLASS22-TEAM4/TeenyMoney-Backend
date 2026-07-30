# 하위2 Task 1 — JwtProvider와 JWT 프로퍼티

> 플랜: [`docs/jwt-security-pipeline.md`](jwt-security-pipeline.md) §Task 1
> 산출물: `global/security/jwt/JwtProvider.java`, `application.properties`, `global/security/jwt/JwtProviderTest.java`

---

## 1. 왜 JwtProvider를 구현해야 했나

### 문제: 서버는 요청마다 "너 누구냐"를 다시 물을 수 없다

TeenyMoney는 REST API이고 `SessionCreationPolicy.STATELESS`로 간다. 세션을 안 쓰기로 했으니 서버 메모리에 "누가 로그인했는지"를 담아두지 않는다. 그러면 `/api/v1/wallet/balance` 같은 요청이 들어올 때 **이 요청이 누구 것인지 알아낼 방법이 필요하다.**

선택지는 두 갈래였다.

| 방식 | 요청마다 하는 일 | 문제 |
|---|---|---|
| 세션/DB 조회 | 세션ID나 토큰을 받아 저장소에서 사용자를 찾는다 | 요청마다 I/O. 상태를 서버가 들고 있어 확장이 어렵다 |
| **JWT (선택)** | 토큰 자체에 담긴 서명을 검증한다 | 저장소 조회 없이 검증 끝. 대신 토큰을 되돌릴 수 없다 |

JWT를 택하면 **사용자 정보를 토큰 안에 넣고, 위조되지 않았음을 서명으로 증명**한다. 그 "넣고 / 증명하는" 일을 하는 컴포넌트가 `JwtProvider`다.

### JwtProvider가 없으면 못 하는 일

이 클래스는 인증 파이프라인의 **맨 아래 계층**이다. 위쪽 전부가 여기에 의존한다.

```
로그인 API (하위3)          ─── createAccessToken/createRefreshToken 이 필요
토큰 재발급 API (하위4)      ─── parse 로 Refresh 검증이 필요
JwtAuthenticationFilter     ─── parse 로 매 요청 Access 검증이 필요  ← 하위2 Task 2
        ↓
    JwtProvider  ← 지금 만든 것
```

즉 **토큰을 만들 수도 없고 읽을 수도 없으면 로그인도, 인증도, 재발급도 시작할 수 없다.** 그래서 플랜의 Task 1이다.

### 왜 Access와 Refresh 두 종류인가

- **Access Token (30분)** — 매 요청 `Authorization` 헤더로 실려 다닌다. 노출 빈도가 높으니 수명을 짧게 둬서, 탈취돼도 피해 창을 좁힌다.
- **Refresh Token (14일)** — Access가 만료됐을 때 새 Access를 받는 데만 쓴다. 노출 빈도가 낮으니 수명을 길게 둬서 사용자가 2주에 한 번만 재로그인하게 한다.

이 분리는 **수명이 다른 두 토큰을 서로 대체할 수 없게 만들어야** 의미가 있다. 그래서 `tokenType` 클레임이 필수다 (아래 §4.1).

---

## 2. 무엇을 만들었나 — 공개 계약

```java
public class JwtProvider {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    public JwtProvider(String secret, long accessExpirationMs, long refreshExpirationMs)

    public String createAccessToken(Long memberId, String role)   // sub, role, tokenType=ACCESS, iat, exp
    public String createRefreshToken(Long memberId)                // sub,       tokenType=REFRESH, iat, exp
    public Claims parse(String token)                              // 서명·만료 검증 후 클레임 반환
}
```

**클레임 구조**

| 클레임 | Access | Refresh | 의미 |
|---|---|---|---|
| `sub` | `"17"` | `"17"` | memberId (JWT 표준상 문자열) |
| `role` | `"PARENT"` / `"CHILD"` | 없음 | 권한. `ROLE_PARENT` 형태로 변환해 쓴다 |
| `tokenType` | `"ACCESS"` | `"REFRESH"` | 토큰 용도 |
| `iat` / `exp` | 있음 | 있음 | 발급/만료 시각 |

**프로퍼티**

```properties
jwt.secret=${JWT_SECRET:roc9Ns8gE2EDKDkYXuy/tHxrKZXoeaWHTMb+eN8YeZM=}
jwt.access-expiration=${JWT_ACCESS_EXPIRATION_MS:1800000}      # 30분
jwt.refresh-expiration=${JWT_REFRESH_EXPIRATION_MS:1209600000}  # 14일
```

---

## 3. 테스트가 확인하는 것

`JwtProviderTest` 4개. 각각 **무엇을 넣어 / 무엇이 나와야 하고 / 왜 그걸 확인하는지**로 적는다.

테스트 시크릿은 `b5ZbpsxM9qd3XTR09Hm/tTbpmTybNbUp/Qc51yyPmk4=` (Base64로 인코딩된 32바이트 키)를 쓴다.

### 3.1 `accessTokenRoundTrip` — 발급한 걸 읽어낼 수 있나

**입력** `createAccessToken(17L, "PARENT")` 로 만든 토큰을 `parse()`에 넣는다.

**기대 결과**
```
claims.getSubject()                  → "17"
claims.get("role", String.class)     → "PARENT"
claims.get("tokenType", String.class) → "ACCESS"
```

**왜 확인하나** — 발급과 파싱은 반드시 **왕복(round-trip)**해야 한다. 클레임 이름을 `role`로 넣고 `roles`로 읽거나, `sub`에 `Long`을 넣었는데 파싱 때 타입이 안 맞으면, 토큰은 정상 생성되지만 필터가 사용자를 식별하지 못한다. 이건 **컴파일도 되고 예외도 안 나면서 인증만 조용히 실패하는** 종류의 버그다. 그래서 세 클레임을 이름·값 단위로 못 박는다.

특히 `getSubject()`가 `"17"`(문자열)인 점이 중요하다. JWT 표준에서 `sub`는 문자열이므로 `Long` 17이 아니라 `"17"`로 나온다. 필터가 `Long.valueOf(claims.getSubject())`로 되돌려야 한다는 사실을 이 테스트가 문서화한다.

### 3.2 `refreshTokenClaims` — Refresh는 Access와 구별되나

**입력** `createRefreshToken(17L)` 로 만든 토큰을 `parse()`에 넣는다.

**기대 결과**
```
claims.getSubject()                   → "17"
claims.get("tokenType", String.class) → "REFRESH"
```

**왜 확인하나** — `tokenType`이 실제로 `"REFRESH"`(대문자)로 들어가는지 확인한다. 이 값이 §4.1의 보안 경계를 만드는 유일한 근거다. 여기서 `"refresh"`(소문자)나 `null`이 나오면 Access/Refresh 구분이 무너진다.

또 Refresh에는 `role`을 **넣지 않는다.** Refresh는 "새 Access를 받을 자격"만 증명하면 되고 권한 판단에는 쓰지 않는다. 권한 정보를 14일짜리 토큰에 박아두면, 부모가 아이로 역할이 바뀌어도 2주간 옛 권한이 살아 있게 된다. 짧은 Access에만 `role`을 담아 재발급 시점마다 갱신되게 한다.

### 3.3 `expiredTokenThrows` — 만료를 만료로 알아채나

**입력** 만료 시각이 **1분 전**인 Access 토큰 (`new JwtProvider(SECRET, -60_000L, -60_000L)`).

**기대 결과** `parse()`가 `ExpiredJwtException`을 **던진다.**

**왜 확인하나** — 두 가지다.

1. **만료가 실제로 강제되는가.** 30분 수명을 정해놨는데 만료 검증이 동작하지 않으면 Access Token이 영구 토큰이 된다. 짧은 수명으로 얻으려던 보안 이점이 전부 사라진다.
2. **만료가 다른 실패와 구별되는가.** `ExpiredJwtException`은 `JwtException`의 하위 타입이다. `parse()`가 이걸 뭉개서 잡지 않고 그대로 던져야, 호출측(`JwtAuthenticationFilter`)이
   ```
   ExpiredJwtException  → AUTH_TOKEN_EXPIRED  "인증이 만료되었습니다. 다시 로그인해 주세요."
   그 외 JwtException   → AUTH_TOKEN_INVALID  "유효하지 않은 인증 정보입니다."
   ```
   로 나눠 처리할 수 있다. 이 구분이 없으면 FE는 "토큰 갱신을 시도해야 하는 상황"과 "재로그인시켜야 하는 상황"을 구별할 수 없다.

> **`-60_000L`인 이유** — 처음엔 플랜대로 `-1L`(1ms 전 만료)이었고 통과했지만, JWT `exp`는 **초 단위(NumericDate)로 직렬화**된다. 1ms 차이는 같은 초로 절삭되어 사라질 수 있고, jjwt는 `exp == now`를 만료로 보지 않는다. 즉 실행 시각의 밀리초 값에 따라 드물게 실패하는 플레이키 테스트였다. 1분 전으로 넉넉히 넘겨 초 단위 절삭과 무관하게 항상 만료로 잡히게 했다.

### 3.4 `tamperedTokenThrows` — 위조를 걸러내나

**입력** **다른 키**(`nDlRA4wqNsD9UWmGExA1MCPvrWiVob6ewIO9ss319jY=`)로 서명한 Access 토큰을, 원래 키를 가진 `provider`로 파싱한다.

**기대 결과** `parse()`가 `JwtException`을 **던진다.**

**왜 확인하나** — **JWT 보안 모델 전체가 이 하나에 걸려 있다.** JWT의 payload는 암호화가 아니라 Base64 인코딩일 뿐이어서 누구나 열어볼 수 있고, 내용을 고쳐 다시 인코딩하는 것도 쉽다. 토큰이 위조되지 않았음을 보장하는 것은 **서명 검증뿐**이다.

서명 검증이 동작하지 않으면 공격자가 `{"sub":"1","role":"PARENT"}` 를 직접 써서 아무 계정으로든 로그인할 수 있다. 이 테스트는 그 시나리오를 재현한다 — 다른 키로 만든 토큰은 형식상 완벽히 올바른 JWT지만, 우리 키로 검증하면 서명이 안 맞으므로 거부돼야 한다.

`verifyWith(key)` 를 빼먹거나 `parseSignedClaims` 대신 검증 없는 파서를 쓰면 이 테스트만 깨진다. 나머지 3개는 통과한다. **가장 위험한 실수를 잡아내는 유일한 테스트**라서 반드시 있어야 한다.

### 실행 결과

```
./gradlew test --tests "com.teenyfin.teenymoney.global.security.jwt.JwtProviderTest"
→ tests="4" skipped="0" failures="0" errors="0"
```

### 4개가 함께 커버하는 것

토큰 검증의 판단은 결국 세 갈래이고, 테스트가 각 갈래를 하나씩 맡는다.

| 상황 | 담당 테스트 | 결과 |
|---|---|---|
| 정상 | 3.1, 3.2 | 클레임 복원 |
| 시간이 지남 | 3.3 | `ExpiredJwtException` |
| 손을 댐 | 3.4 | `JwtException` |

---

## 4. 설계 판단과 근거

### 4.1 `tokenType` 클레임을 왜 넣었나

Access와 Refresh는 같은 키로 서명한다. 그러면 **서명 검증만으로는 둘을 구별할 수 없다.** `tokenType`이 없으면 14일짜리 Refresh Token을 `Authorization: Bearer` 헤더에 실어 보내는 것만으로 모든 API를 호출할 수 있다. Access 수명을 30분으로 줄인 의미가 사라진다.

`tokenType`을 클레임에 박아두면 **서명된 데이터의 일부**가 되므로 공격자가 `REFRESH`를 `ACCESS`로 고칠 수 없다 (고치면 서명이 깨져 3.4처럼 거부된다).

대문자 `ACCESS`/`REFRESH`로 고정한 것은 플랜의 Global Constraints다. 대소문자가 섞이면 `equals` 비교가 조용히 실패한다.

> 이 값을 실제로 강제하는 검증은 **Task 2의 `JwtAuthenticationFilter`**에 있다. Task 1은 클레임을 정확히 심는 것까지가 책임이다.

### 4.2 secret을 왜 Base64로 받나

HS256은 최소 256bit(32바이트) 키를 요구한다. 두 방식을 비교하면:

| | 사람이 읽는 문자열의 바이트 | **Base64 디코딩 (선택)** |
|---|---|---|
| 예 | `"teenymoney-secret-key-12345678"` | `openssl rand -base64 32` |
| 길이 검사 통과 | 32자면 통과 | 디코딩 후 32바이트 |
| 실제 엔트로피 | 문자당 ~4~5bit → **150bit 미만** | **256bit 전부** |

길이만 맞추면 검사는 통과하지만, 사람이 읽을 수 있는 문자열은 엔트로피가 낮아 무차별 대입에 훨씬 약하다. Base64를 전제로 하면 `openssl rand -base64 32`라는 **정답 생성법이 하나로 정해지고**, 팀원이 임의 문자열을 넣을 여지가 없다.

### 4.3 `parse()`가 예외를 왜 그대로 던지나

`parse()`가 `Optional<Claims>`나 `null`을 반환하면 **실패 사유가 사라진다.** 만료와 위조는 사용자에게 다른 메시지를 보여야 하고(§3.3), FE는 그 차이로 재발급 시도 여부를 판단한다. jjwt의 예외 타입을 그대로 전파해 호출측이 `catch` 절로 구분하게 했다.

### 4.4 프로퍼티에 개발 기본값을 왜 두나

```properties
jwt.secret=${JWT_SECRET:roc9Ns8gE2EDKDkYXuy/tHxrKZXoeaWHTMb+eN8YeZM=}
```

기본값이 없으면(`${JWT_SECRET}`) **팀원 전원이 환경변수를 설정해야 앱이 뜬다.** 인증과 무관한 화면을 개발하는 사람도 막힌다. 기본값을 두면 `git clone` 후 바로 기동된다.

이 기본값은 저장소에 공개돼 있으므로 **비밀이 아니다.** 운영에서는 `JWT_SECRET` 환경변수로 반드시 override 한다. 플랜의 완료기준 "앱이 환경변수 없이도 기동(개발 기본값)"이 이 결정이다.

> 엄격 모드로 바꾸려면 기본값을 제거하면 되지만, 그건 `authenticated` 강제로 전환하는 시점에 README·`setenv.sh` 갱신과 함께 팀 공지 후 처리한다.

### 4.5 왜 `@Component`가 아닌가

`JwtProvider`에는 스프링 애노테이션이 하나도 없다. 순수 자바 클래스이고 `SecurityConfig`의 `@Bean`으로 등록된다 (Task 4).

이 프로젝트는 컨텍스트가 두 개다.

```
루트 컨텍스트   RootConfig, RedisConfig, SecurityConfig   ← 시큐리티 필터체인이 여기 붙는다
자식 컨텍스트   ServletConfig  → domain, global 을 컴포넌트 스캔
```

`@Component`를 붙이면 `ServletConfig`가 `global` 패키지를 스캔해 **자식 컨텍스트**에 빈을 만든다. 그런데 필터체인은 루트 컨텍스트에 있어서 자식의 빈을 볼 수 없다. 결과적으로 필터가 체인에 붙지 않고 **인증이 조용히 통째로 동작하지 않는다.** 애노테이션 없는 클래스로 두면 이 실수가 애초에 불가능하다.

부수 효과로 테스트에서 스프링 컨텍스트 없이 `new JwtProvider(...)`로 바로 쓸 수 있어 `JwtProviderTest`가 밀리초 단위로 끝난다.

---

## 5. 이 Task가 만들지 않은 것

| 항목 | 어디서 |
|---|---|
| `tokenType`이 ACCESS인지 **강제**하는 검증 | Task 2 `JwtAuthenticationFilter` |
| `SecurityContext`에 인증 채우기 | Task 2 |
| 401/403 JSON 응답 | Task 3 |
| 빈 등록·필터체인 배선 | Task 4 |
| Refresh Token 저장·회전·무효화 | 하위4 |
| 로그인·회원가입 (토큰 발급 호출측) | 하위3 |

Task 1은 **토큰을 정확히 만들고 정확히 읽는 것**까지다. 그걸로 무엇을 할지는 위 계층의 몫이다.
