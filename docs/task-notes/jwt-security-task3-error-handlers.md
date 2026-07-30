# 하위2 Task 3 — 인증 401·인가 403 실패 응답 핸들러

> 플랜: [`docs/task-notes/jwt-security-pipeline.md`](jwt-security-pipeline.md) §Task 3
> 산출물: `global/security/ErrorResponseWriter.java`, `global/security/RestAuthenticationEntryPoint.java`, `global/security/RestAccessDeniedHandler.java`, `global/security/SecurityHandlersTest.java`
> 선행: [Task 2 — JwtAuthenticationFilter](jwt-security-task2-authentication-filter.md)

---

## 1. 왜 이걸 구현했나

### 출발점: Task 2가 남긴 메모를 읽는 사람이 없었다

Task 2에서 필터가 토큰에 문제가 있을 때 **메모를 남기고 요청을 통과**시켰다.

```java
request.setAttribute(AUTH_ERROR_ATTRIBUTE, AuthErrorCode.AUTH_TOKEN_EXPIRED);
```

그런데 **이 메모를 읽는 코드가 없다.** 메모는 요청이 끝나면 그냥 사라진다. 사용자는 아무 응답도 받지 못하거나, 받아도 왜 거부됐는지 알 수 없다.

Task 3은 **그 메모를 읽어 사용자에게 보여줄 JSON을 만드는 일**이다.

### 왜 `@RestControllerAdvice`로는 안 되나 — 실행 위치가 다르다

이 프로젝트에는 이미 예외를 JSON으로 바꿔주는 곳이 있다. 그런데 인증/인가 실패는 **거기까지 도달하지 못한다.**

```
요청
 ├─ 서블릿 필터 체인
 │   ├─ JwtAuthenticationFilter          (Task 2)
 │   └─ 인가 판단 → 실패!  ← 여기서 끝난다. 아래로 안 내려간다
 ├─ DispatcherServlet
 │   └─ @RestControllerAdvice            ← 여기까지 와야 예외를 JSON으로 바꿔준다
 └─ Controller
```

`@RestControllerAdvice`는 **`DispatcherServlet` 안에서** 동작한다. 인증/인가 실패는 그보다 **위(필터 체인)에서** 결정되므로, `DispatcherServlet`에 들어가기 전에 응답이 끝나야 한다.

그래서 **필터 단계에서 직접 JSON을 쓰는 코드**가 필요하다. 그게 이 Task의 산출물이다.

### 그 코드를 어디에 두나 — Spring Security가 자리를 정해놨다

Spring Security는 두 실패를 구분하고, 각각에 대해 **꽂아 넣을 인터페이스**를 제공한다.

| 상황 | 뜻 | 인터페이스 | 상태 코드 |
|---|---|---|---|
| **인증** 실패 | 네가 누군지 모르겠다 | `AuthenticationEntryPoint` | **401** |
| **인가** 실패 | 누군지는 알지만 권한이 없다 | `AccessDeniedHandler` | **403** |

우리가 만든 두 클래스가 각 인터페이스의 구현체다.

```
토큰 없음 / 만료 / 위조         → 401  RestAuthenticationEntryPoint
CHILD가 PARENT 전용 API 호출    → 403  RestAccessDeniedHandler
```

**401과 403을 구별하는 게 중요하다.** 401은 "로그인하면 될지도 모른다", 403은 "로그인해도 안 된다"다. FE가 401에서는 재로그인을 유도하고, 403에서는 "권한이 없습니다"를 띄운다. 둘을 섞으면 자녀가 부모 전용 화면에서 계속 로그인 화면으로 튕긴다.

---

## 2. 무엇을 만들었나

파일 3개다.

| 파일 | 역할 | 크기 |
|---|---|---|
| `ErrorResponseWriter.java` | JSON을 실제로 쓰는 **공용 도구** | 26줄 |
| `RestAuthenticationEntryPoint.java` | **401** 담당. 메모를 읽어 사유를 결정 | 27줄 |
| `RestAccessDeniedHandler.java` | **403** 담당 | 22줄 |

### ErrorResponseWriter — JSON 쓰는 일을 한 곳에 모았다

```java
final class ErrorResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(ApiResponse.error(errorCode)));
    }
}
```

세 줄이 각각 일을 한다.

1. **상태 코드**를 `ErrorCode`에서 가져온다. `AUTH_UNAUTHORIZED`는 401, `AUTH_FORBIDDEN`은 403이 이미 enum에 박혀 있다. 핸들러가 숫자를 직접 쓰지 않는다
2. **`charset=UTF-8`** 을 명시한다. 없으면 `"로그인이 필요합니다"` 같은 한글 메시지가 깨진다
3. **`ApiResponse.error(errorCode)`** 로 감싼다 — 프로젝트 공통 응답 형식

두 핸들러가 같은 코드를 쓰므로 한 곳에 모았다. `package-private`(`final class`, `static` 메서드)이라 `global.security` 패키지 밖에서는 보이지 않는다 — 이 도구는 두 핸들러만 쓰면 된다.

### RestAuthenticationEntryPoint — 메모를 읽는 곳

```java
Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
ErrorCode errorCode = (attribute instanceof ErrorCode)
        ? (ErrorCode) attribute
        : CommonErrorCode.AUTH_UNAUTHORIZED;
ErrorResponseWriter.write(response, errorCode);
```

**Task 2와 Task 3이 여기서 만난다.** 필터가 남긴 메모가 있으면 그 코드로, 없으면 기본값으로 응답한다.

| 필터가 남긴 메모 | 응답 코드 | 사용자에게 보이는 메시지 |
|---|---|---|
| 없음 (토큰 자체가 없었다) | `AUTH_UNAUTHORIZED` | "로그인이 필요합니다." |
| `AUTH_TOKEN_EXPIRED` | `AUTH_TOKEN_EXPIRED` | "인증이 만료되었습니다. 다시 로그인해 주세요." |
| `AUTH_TOKEN_INVALID` | `AUTH_TOKEN_INVALID` | "유효하지 않은 인증 정보입니다." |

`JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE` 상수를 쓰는 게 핵심이다. 양쪽이 `"authError"`라고 각자 문자열을 쓰면, 오타 하나로 **메모가 항상 유실되고 늘 "로그인이 필요합니다"만 나간다.** 만료인지 위조인지 알 수 없게 된다.

### RestAccessDeniedHandler — 항상 403

```java
ErrorResponseWriter.write(response, CommonErrorCode.AUTH_FORBIDDEN);
```

여기는 분기가 없다. 인가 실패는 **이미 인증이 끝난 뒤**의 문제이므로 토큰 관련 메모를 볼 필요가 없다. 사유도 하나뿐이다 — 권한이 부족하다.

> **왜 사유를 자세히 안 알려주나** — "PARENT 권한이 필요합니다" 같은 메시지는 공격자에게 **어떤 권한이 있으면 되는지 알려주는 힌트**가 된다. "접근 권한이 없습니다"로 통일한다.

---

## 3. 테스트가 확인하는 것

`SecurityHandlersTest` 3개. Task 1·2와 같은 방식으로 *입력* / *기대 결과* / *왜* 로 적는다.

### 테스트 도구

`MockHttpServletRequest`·`MockHttpServletResponse`만 쓴다. 톰캣도 Spring 컨텍스트도 없다. 핸들러는 **요청과 응답 객체만 받는 순수 함수**에 가까워서 이렇게 테스트할 수 있다.

`AuthenticationException`은 추상 클래스라 직접 만들 수 없으므로 테스트용 하위 클래스를 둔다.

```java
static class StubAuthException extends AuthenticationException {
    StubAuthException() { super("unauthorized"); }
}
```

핸들러가 이 예외를 **사용하지 않는다**는 점이 중요하다. 응답 사유는 예외가 아니라 **request attribute**에서 온다. 그래서 어떤 예외를 넣어도 결과가 같다.

### 3.1 `entryPointDefaultUnauthorized` — 메모가 없을 때 기본 응답

**입력** attribute가 비어 있는 요청 (= 토큰을 아예 안 보낸 경우)

**기대 결과**
```
status      : 401
contentType : application/json;charset=UTF-8
body        : {"success":false,"code":"AUTH_UNAUTHORIZED","message":"로그인이 필요합니다.","data":null}
```

**왜 확인하나** — 세 가지다.

1. **`instanceof` 분기의 else 쪽이 동작하나.** attribute가 `null`일 때 `(ErrorCode) null`로 캐스팅하면 나중에 `errorCode.getStatus()`에서 **NPE가 터져 500이 나간다.** `instanceof`가 `null`을 거르는지 확인한다
2. **응답이 `ApiResponse` 형식인가.** `"success":false`를 검사한다. FE는 이 필드 하나로 성공/실패를 분기하기로 했으므로, 인증 실패 응답만 형식이 다르면 FE가 처리하지 못한다
3. **`contentType`에 charset이 있나.** 없으면 `"로그인이 필요합니다"`가 깨진다. 이 테스트에서만 `contentType`을 출력해 확인한다

### 3.2 `entryPointUsesAttribute` — 메모가 있으면 그 사유로 응답하나

**입력** attribute에 `AuthErrorCode.AUTH_TOKEN_EXPIRED`를 넣은 요청

**기대 결과**
```
status : 401
body   : {"success":false,"code":"AUTH_TOKEN_EXPIRED","message":"인증이 만료되었습니다. 다시 로그인해 주세요.","data":null}
```

**왜 확인하나** — **Task 2와 Task 3을 잇는 유일한 검증**이다.

필터가 메모를 남기고(Task 2 §3.3에서 확인), 진입점이 그 메모를 읽는다(여기). 두 테스트가 각자 자기 쪽만 보므로, **연결 지점의 계약(attribute 키와 값의 타입)이 맞는지**가 이 테스트에 걸려 있다.

만약 이 검증이 없으면:
- 필터는 `AUTH_TOKEN_EXPIRED`를 남긴다 → 자기 테스트 통과
- 진입점은 `AUTH_UNAUTHORIZED`로 응답한다 → 자기 테스트 통과
- **둘을 합치면 만료 사유가 유실된다** → 아무 테스트도 실패하지 않는다

`AuthErrorCode`(도메인)와 `CommonErrorCode`(공통)는 **서로 다른 enum**이지만 둘 다 `ErrorCode` 인터페이스를 구현한다. 진입점이 `ErrorCode` 타입으로 받으므로 어느 enum이 와도 동작한다 — 이 테스트가 그 다형성이 실제로 되는지도 함께 확인한다.

### 3.3 `accessDeniedForbidden` — 403이 401과 구별되나

**입력** `AccessDeniedException`

**기대 결과**
```
status : 403
body   : {"success":false,"code":"AUTH_FORBIDDEN","message":"접근 권한이 없습니다.","data":null}
```

**왜 확인하나** — **401이 아니라 403이어야 한다**는 것이 요점이다.

핸들러가 실수로 401을 내면 FE는 "로그인이 안 됐구나" 판단하고 사용자를 로그인 화면으로 보낸다. 그런데 사용자는 이미 로그인돼 있다. **로그인 → 접근 → 튕김 → 로그인**이 무한 반복된다. 자녀가 부모 전용 화면을 누를 때마다 겪게 된다.

`403`이라는 숫자는 코드에 없다. `CommonErrorCode.AUTH_FORBIDDEN`이 `HttpStatus.FORBIDDEN`을 들고 있고 `ErrorResponseWriter`가 `errorCode.getStatus().value()`로 꺼낸다. 이 테스트는 **enum에 박힌 상태 코드가 실제 응답까지 전달되는지**를 확인한다.

### 실행 결과

```
./gradlew test --tests "com.teenyfin.teenymoney.global.security.SecurityHandlersTest"

SecurityHandlersTest
  ✓ 진입점: authError 속성이 없으면 401 AUTH_UNAUTHORIZED JSON을 쓴다
        authError 속성 : null
        status        : 401   (기대 401)
        contentType   : "application/json;charset=UTF-8"
        body          : {"success":false,"code":"AUTH_UNAUTHORIZED","message":"로그인이 필요합니다.","data":null}

  ✓ 진입점: authError 속성이 있으면 그 코드로 401 JSON을 쓴다
        authError 속성 : AUTH_TOKEN_EXPIRED
        status        : 401   (기대 401)
        body          : {"success":false,"code":"AUTH_TOKEN_EXPIRED","message":"인증이 만료되었습니다. 다시 로그인해 주세요.","data":null}

  ✓ 인가거부: 403 AUTH_FORBIDDEN JSON을 쓴다
        status        : 403   (기대 403)
        body          : {"success":false,"code":"AUTH_FORBIDDEN","message":"접근 권한이 없습니다.","data":null}

전체: 20 tests, failures=0, errors=0
     (JwtProviderTest 4 + JwtAuthenticationFilterTest 4 + 이 파일 3 + 기존 9)
```

응답 본문을 그대로 출력하므로 **FE에 전달할 JSON을 눈으로 확인할 수 있다.** 한글 메시지가 깨지지 않는 것도 함께 드러난다.

---

## 4. 설계 판단과 근거

### 4.1 왜 사유를 예외가 아니라 attribute에서 읽나

`commence`는 `AuthenticationException`을 파라미터로 받는다. 거기서 사유를 꺼내는 게 자연스러워 보인다. 그런데 안 쓴다.

**Task 2 §4.1의 결정 때문이다.** 필터는 인증 실패에도 요청을 통과시킨다. 예외를 던지지 않으므로, 진입점에 도달할 때의 예외는 **Spring Security가 만든 일반적인 예외**(`InsufficientAuthenticationException` 등)이고 **JWT 관련 정보가 전혀 없다.**

```
필터: 메모 남기고 통과        (예외 안 던짐)
  ↓
인가 규칙: 인증 안 됨 → 거부   (Spring Security가 일반 예외 생성)
  ↓
진입점: 이 예외는 사유를 모른다 → attribute를 봐야 한다
```

즉 attribute가 **유일한 정보 경로**다. Task 2의 설계가 Task 3의 구현 방식을 결정했다.

### 4.2 왜 `ObjectMapper`를 `static final`로 두나

```java
private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
```

`ObjectMapper` 생성은 무겁다(내부에 직렬화 캐시를 만든다). 인증 실패마다 새로 만들면 낭비다. 그리고 `ObjectMapper`는 **설정을 바꾸지 않는 한 스레드 안전**하므로 하나를 공유해도 된다.

> 앞선 Codex 구현은 이걸 빈으로 주입받게 했다. 그것도 맞는 방법이고 설정 커스터마이징이 필요해지면 그 편이 낫다. 지금은 기본 설정으로 충분하고, `static`으로 두면 **핸들러가 스프링 없이 `new`로 만들어져 테스트가 단순해진다.**

### 4.3 왜 `ErrorResponseWriter`가 package-private인가

`final class` + `static` 메서드 + `private` 생성자다. `public`이 아니다.

이 도구는 **`global.security` 패키지의 두 핸들러만** 쓴다. `public`으로 열면 서비스나 컨트롤러에서 `ErrorResponseWriter.write(...)`를 직접 호출하는 코드가 생길 수 있는데, 컨트롤러에서는 **`ApiResponse`를 반환하거나 예외를 던지면 `@RestControllerAdvice`가 처리**하는 것이 맞다. 응답을 직접 쓰는 건 필터 단계의 예외적인 방식이다.

`private` 생성자는 인스턴스를 만들 이유가 없다는 표시다.

### 4.4 왜 상태 코드를 `ErrorCode`에서 가져오나

```java
response.setStatus(errorCode.getStatus().value());   // 401 / 403을 코드에 안 쓴다
```

`AUTH_UNAUTHORIZED`가 401이고 `AUTH_FORBIDDEN`이 403이라는 사실은 **enum 한 곳에만** 있다.

```java
AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
```

핸들러에 `response.setStatus(401)`이라고 쓰면 **같은 사실이 두 곳에 생긴다.** 나중에 코드를 추가하거나 상태를 바꿀 때 한쪽만 고치면 어긋난다. `ErrorResponseWriter`가 enum에서 꺼내므로 **핸들러는 어떤 코드인지만 정하고 숫자는 모른다.**

### 4.5 왜 인가 실패에는 구체적 사유를 안 주나

`RestAccessDeniedHandler`는 항상 `AUTH_FORBIDDEN`이다. "PARENT 권한이 필요합니다" 같은 메시지를 만들지 않는다.

권한 구조를 알려주는 메시지는 **공격자에게 정보를 준다.** 어떤 역할이 어떤 API에 접근 가능한지 응답으로 알려주면 공격 대상을 좁히기 쉬워진다. 정당한 사용자에게는 "접근 권한이 없습니다"만으로 충분하다 — 자녀가 부모 기능을 쓸 수 없다는 건 앱 화면에서 이미 안내되어야 할 정보다.

---

## 5. 이 Task가 만들지 않은 것

**여전히 아무것도 동작하지 않는다.** 핸들러를 만들었지만 Spring Security에 등록하지 않았다.

```
Task 2  필터를 만들었다
Task 3  메모를 읽어 401/403 응답을 만들었다   ← 지금 여기
Task 4  필터·핸들러를 실제 통로에 끼워넣는다  ← 이때부터 동작한다
```

| 항목 | 어디서 |
|---|---|
| 핸들러를 **필터체인에 등록**하기 (`exceptionHandling`) | Task 4 `SecurityConfig` |
| 필터를 필터체인에 등록하기 | Task 4 |
| 실제 HTTP 요청으로 401/403 확인 | Task 4 이후 (지금은 단위 테스트뿐) |
| `authenticated` 강제 — 이게 없으면 401이 실제로 발생하지 않는다 | 하위3 이후, 팀 조율 |
| 403이 실제로 발생하는 상황(`@PreAuthorize`) | 첫 role 게이팅 이슈 |

### 지금 상태에서는 401도 403도 발생하지 않는다

Task 4에서 핸들러를 등록해도, 인가 규칙이 `permitAll`이면 **모든 요청이 허용되므로 실패가 일어나지 않는다.** 진입점도 핸들러도 호출될 일이 없다.

그래서 Task 3의 산출물은 **`authenticated`로 전환하는 시점에 비로소 쓰이기 시작한다.** 미리 만들어 두는 이유는, 전환 시점에 401 응답 형식까지 새로 만들려면 그 PR이 커지고 FE와의 계약을 급하게 정해야 하기 때문이다. 형식을 먼저 확정해두면 전환은 인가 규칙 한 줄 변경으로 끝난다.
