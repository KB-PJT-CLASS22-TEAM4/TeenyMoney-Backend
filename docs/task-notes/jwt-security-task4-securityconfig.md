# 하위2 Task 4 — SecurityConfig 배선 (permitAll 유지)

> 플랜: [`docs/task-notes/jwt-security-pipeline.md`](jwt-security-pipeline.md) §Task 4
> 산출물: `config/SecurityConfig.java`(수정), `config/InfrastructureConfigTest.java`(확장)
> 선행: [Task 3 — 실패 응답 핸들러](jwt-security-task3-error-handlers.md)

---

## 1. 왜 이걸 구현했나

### 출발점: Task 1~3을 다 만들었는데 아무것도 동작하지 않았다

Task 1~3에서 클래스 6개를 만들었다. 그런데 **전부 아무 데도 연결되지 않은 상태**였다.

```
JwtProvider              만들어졌다. 아무도 안 쓴다
JwtAuthenticationFilter  만들어졌다. 필터체인에 없다 → 요청이 지나가지 않는다
Rest*EntryPoint/Handler  만들어졌다. 등록 안 됐다 → 호출되지 않는다
```

Task 4는 **조립**이다. 새 로직을 만들지 않고, 이미 있는 조각을 Spring Security에 꽂는다.

### 두 가지를 해야 한다

**1. 객체를 만들어 스프링에 등록한다 (빈 등록)**
`new JwtProvider(...)`를 누군가는 호출해야 한다. 그리고 `jwt.secret` 같은 설정값을 읽어 넣어야 한다.

**2. 만든 객체를 필터체인에 꽂는다 (배선)**
Spring Security에게 "이 필터를 여기에 넣고, 인증 실패하면 이 핸들러를 불러라"고 알려준다.

### 왜 `@Component`가 아니라 `@Bean`인가 — 이 프로젝트에서 가장 위험한 함정

`JwtProvider`나 필터에 `@Component`를 붙이는 게 더 간단해 보인다. 그런데 이 프로젝트에서는 **동작하지 않는다.**

이 프로젝트는 스프링 컨텍스트가 **두 개**다.

```
루트 컨텍스트    RootConfig, RedisConfig, SecurityConfig   ← 시큐리티 필터체인이 여기 붙는다
      ↓ (자식은 부모를 볼 수 있지만, 부모는 자식을 못 본다)
자식 컨텍스트    ServletConfig → domain, global 패키지를 컴포넌트 스캔
```

`WebConfig.java`가 이렇게 나눠놨다.

```java
protected Class<?>[] getRootConfigClasses() {
    return new Class[] { RootConfig.class, RedisConfig.class, SecurityConfig.class };
}
protected Class<?>[] getServletConfigClasses() {
    return new Class[] { ServletConfig.class };
}
```

`JwtAuthenticationFilter`에 `@Component`를 붙이면 `ServletConfig`가 `global` 패키지를 스캔해 **자식 컨텍스트에** 빈을 만든다. 그런데 필터체인은 **루트 컨텍스트**에 있고, **부모는 자식의 빈을 볼 수 없다.**

결과: 빈은 만들어지고 앱은 정상 기동하는데 **필터가 체인에 붙지 않는다.** 인증이 통째로 동작하지 않으면서 **예외도 로그도 없다.**

그래서 보안 빈은 전부 `SecurityConfig`의 `@Bean`으로 등록한다. Task 1~3의 클래스들에 스프링 애노테이션을 하나도 붙이지 않은 이유가 이것이다 — 애초에 이 실수가 불가능해진다.

---

## 2. 무엇을 만들었나

### 등록한 빈 6개

| 빈 | 역할 | 언제 쓰이나 |
|---|---|---|
| `jwtProvider` | 토큰 발급·검증 | 필터가 매 요청, 로그인(하위3)이 발급 시 |
| `jwtAuthenticationFilter` | 매 요청 인증 | 모든 요청 |
| `restAuthenticationEntryPoint` | 401 JSON | 인가가 인증 실패로 거부할 때 |
| `restAccessDeniedHandler` | 403 JSON | 권한 부족으로 거부할 때 |
| `passwordEncoder` | 비밀번호 해시(BCrypt) | 회원가입·로그인(하위3) |
| `securityFilterChain` | 위 조각들의 배선 | 앱 기동 시 1회 |

`passwordEncoder`는 하위2에서 쓰지 않는다. 하위3이 필요하지만 **보안 빈이라 루트 컨텍스트에 있어야** 하므로 여기서 미리 등록한다. BCrypt 해시는 60자여서 `T_MBR_INFO_M.password VARCHAR(255)`에 들어간다.

### 설정값 주입

```java
@Value("${jwt.secret}")
private String jwtSecret;
@Value("${jwt.access-expiration}")
private long accessExpirationMs;
@Value("${jwt.refresh-expiration}")
private long refreshExpirationMs;
```

Task 1에서 `JwtProvider`가 만료 시간을 하드코딩하지 않고 생성자로 받게 만든 것이 여기서 값을 한다. 프로퍼티를 읽는 책임이 `SecurityConfig`에 있고, `JwtProvider`는 설정 파일의 존재를 모른다.

### 필터체인 배선

```java
http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(jwtAuthenticationFilter(),
                UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(handling -> handling
                .authenticationEntryPoint(restAuthenticationEntryPoint())  // 401
                .accessDeniedHandler(restAccessDeniedHandler()))           // 403
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
```

한 줄씩 무엇을 하는지:

| 설정 | 하는 일 | 안 하면 |
|---|---|---|
| `csrf.disable()` | CSRF 토큰 검사 끔 | 모든 POST/PUT/DELETE가 403. 토큰 인증에는 CSRF 토큰이 불필요하다 |
| `STATELESS` | 세션을 만들지 않음 | 서버가 인증 상태를 들고 있게 되어 JWT를 쓴 의미가 없어진다 |
| `addFilterBefore` | 필터를 인가 판단 **앞**에 끼움 | **필터가 실행되지 않는다** (Task 2가 무의미) |
| `exceptionHandling` | 401/403 응답 담당자 지정 | Spring Security 기본 응답이 나가 `ApiResponse` 형식이 깨진다 |
| `permitAll` | 모든 요청 허용 | (아래 §4.2) |

---

## 3. 테스트가 확인하는 것

Task 1~3은 새 테스트 파일을 만들었지만, Task 4는 **기존 `InfrastructureConfigTest`를 확장**한다. 검증 대상이 "특정 클래스의 동작"이 아니라 "스프링 컨텍스트가 제대로 조립됐는가"이기 때문이다.

이 테스트는 **실제로 스프링 컨텍스트를 띄운다.** Task 1~3의 테스트와 성격이 다르다.

| | Task 1~3 테스트 | 이 테스트 |
|---|---|---|
| 스프링 컨텍스트 | 안 띄움 | **띄움** (`@ContextConfiguration`) |
| 대상 | 클래스 하나의 동작 | 조립 결과 |
| 속도 | 밀리초 | 수 초 |

### 3.0 `@TestPropertySource`에 jwt 값을 넣어야 한다

```java
@TestPropertySource(properties = {
        "redis.host=localhost",
        "redis.port=6379",
        "jwt.secret=b5ZbpsxM9qd3XTR09Hm/tTbpmTybNbUp/Qc51yyPmk4=",
        "jwt.access-expiration=1800000",
        "jwt.refresh-expiration=1209600000"
})
```

`SecurityConfig`가 `@Value`로 이 값들을 읽는다. 없으면 **컨텍스트 자체가 뜨지 않아** 테스트 3개가 전부 실패한다. `application.properties`에 기본값이 있지만, 이 테스트는 `@ContextConfiguration`으로 `RedisConfig`·`SecurityConfig`만 띄우고 `RootConfig`(`@PropertySource` 보유)를 포함하지 않으므로 프로퍼티가 로드되지 않는다.

### 3.1 `securityBeansAreRegistered` — 보안 빈이 루트 컨텍스트에 있나

**입력** `RedisConfig` + `SecurityConfig`만으로 띄운 컨텍스트

**기대 결과**
```
getBean(JwtProvider.class)             → null 아님
getBean(JwtAuthenticationFilter.class) → null 아님
getBean(PasswordEncoder.class)         → null 아님
```

**왜 확인하나** — §1에서 말한 **컨텍스트 함정을 잡는 테스트**다.

`@Component`로 잘못 등록하면 빈이 자식 컨텍스트에 만들어진다. 이 테스트는 `SecurityConfig`만 있는 컨텍스트에서 빈을 찾으므로, 자식 컨텍스트에 있는 빈은 **찾지 못하고 `NoSuchBeanDefinitionException`이 난다.**

앱을 띄워서 확인하려면 톰캣을 올리고 실제 요청을 보내야 하는데, 그때도 "인증이 안 된다"는 증상만 보이고 원인이 컨텍스트 분리라는 걸 알기 어렵다. 이 테스트는 **몇 초 안에 정확한 원인을 지목한다.**

**red 확인 결과**
```
InfrastructureConfigTest > securityBeansAreRegistered() FAILED
    org.springframework.beans.factory.NoSuchBeanDefinitionException
3 tests completed, 1 failed
```
`SecurityConfig` 수정 전에는 빈이 없어 정확히 이 예외가 났다.

### 3.2 `securityFilterChainIsRegistered` — 기존 테스트가 계속 통과하나

```java
assertNotNull(applicationContext.getBean("springSecurityFilterChain"));
```

Task 4 전에도 있던 테스트다. `SecurityConfig`를 대폭 고쳤으니 **필터체인이 여전히 만들어지는지** 확인하는 회귀 테스트 역할을 한다.

`springSecurityFilterChain`은 `@EnableWebSecurity`가 자동으로 만드는 빈 이름이고, `WebConfig`의 `DelegatingFilterProxy`가 이 이름으로 필터를 찾는다.

```java
// WebConfig.java
new DelegatingFilterProxy("springSecurityFilterChain")
```

이름이 어긋나면 필터체인이 서블릿 컨테이너에 연결되지 않는다.

### 실행 결과

```
./gradlew clean test

config.InfrastructureConfigTest                  tests=3   failures=0 errors=0
domain.auth.exception.AuthErrorCodeTest          tests=2   failures=0 errors=0
global.exception.ErrorCodeTest                   tests=2   failures=0 errors=0
global.response.ApiResponseFormatTest            tests=3   failures=0 errors=0
global.security.SecurityHandlersTest             tests=3   failures=0 errors=0
global.security.jwt.JwtAuthenticationFilterTest  tests=4   failures=0 errors=0
global.security.jwt.JwtProviderTest              tests=4   failures=0 errors=0
합계: 21
```

### 이 테스트가 확인하지 못하는 것

**필터가 실제로 요청에 개입하는지는 확인하지 않는다.** 빈이 등록됐고 필터체인이 만들어졌다는 것까지다.

`addFilterBefore`의 위치가 잘못됐어도 이 테스트는 통과한다. 그걸 검증하려면 `MockMvc`로 실제 요청을 보내야 하는데, **`permitAll` 상태에서는 인증 성공/실패가 응답에 드러나지 않아** 의미 있는 검증을 만들기 어렵다. `authenticated`로 전환하는 시점에 통합 테스트를 추가하는 것이 맞다.

---

## 4. 설계 판단과 근거

### 4.1 왜 `UsernamePasswordAuthenticationFilter` 앞에 끼우나

```java
.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
```

우리는 폼 로그인을 쓰지 않으므로 `UsernamePasswordAuthenticationFilter`가 실제로 동작하지는 않는다. 그런데도 이걸 기준으로 삼는 이유는 **필터체인에서 그 위치가 "인가 판단보다 앞"이기 때문**이다.

```
... → JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter → ... → 인가 판단 → ...
       ↑ 여기 들어간다                                                        ↑ SecurityContext를 읽는다
```

인증은 인가보다 **먼저** 채워져야 한다. 순서가 뒤바뀌면 인가 규칙이 항상 빈 `SecurityContext`를 보고 모든 요청을 거부한다.

Spring Security는 절대 위치 지정을 제공하지 않고 "어떤 필터 기준 앞/뒤"로만 지정할 수 있다. 그래서 관례적으로 이 필터를 기준점으로 쓴다.

### 4.2 왜 `permitAll`을 유지하나 — 이 Task에서 가장 중요한 결정

```java
.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
```

인증 파이프라인을 다 만들어놓고 **강제하지 않는다.** 플랜의 Global Constraints가 명시한 사항이다.

**이유 1 — 토큰을 발급할 수단이 아직 없다.**
로그인 API는 하위3이다. 지금 `authenticated()`로 켜면 **아무도 토큰을 얻을 수 없는데 모든 API가 401**이 된다. 다른 팀원이 작업 중인 지갑·퀘스트·결제 브랜치가 머지되는 순간 전부 막힌다.

**이유 2 — 공개 경로 목록을 아직 확정할 수 없다.**
`authenticated()`로 바꾸려면 "어디는 토큰 없이 되는가"를 정해야 한다. 로그인·회원가입·헬스체크·Swagger는 명백하지만, 각 도메인이 개발 중이라 목록이 계속 바뀐다. 지금 목록을 박으면 도메인마다 이 파일을 고치며 충돌한다.

**이유 3 — 전환 비용이 작다.**
파이프라인이 완성돼 있으므로, 전환은 이 한 줄을 바꾸는 것이다.

```java
// 지금
.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

// 전환 후
.authorizeHttpRequests(authorize -> authorize
        .requestMatchers("/api/v1/auth/**", "/api/v1/health/**", "/swagger-ui/**").permitAll()
        .anyRequest().authenticated());
```

Task 1~3의 코드는 **한 줄도 바뀌지 않는다.** 필터도, 핸들러도 그대로 쓰인다.

> **현재 상태의 의미** — 파이프라인은 살아 있다. 유효한 Access 토큰을 보내면 `SecurityContext`가 채워지고 `@AuthenticationPrincipal`이 동작한다. 다만 **토큰이 없거나 잘못돼도 요청이 거부되지 않는다.** 하위3이 컨트롤러를 만들 때 인증된 사용자를 받아쓰는 코드를 미리 작성해둘 수 있다.

### 4.3 왜 `@EnableMethodSecurity`를 넣지 않나

`@PreAuthorize("hasRole('PARENT')")`를 쓰려면 이 애노테이션이 필요하다. 그런데 지금 넣지 않는다. 넣더라도 **`SecurityConfig`가 아니라 `ServletConfig`에 넣어야 한다.**

`@EnableMethodSecurity`는 **자기가 속한 컨텍스트의 빈에만** 프록시를 건다. 컨트롤러와 서비스는 `ServletConfig`가 스캔하는 **자식 컨텍스트**에 있다.

```
루트 컨텍스트 (SecurityConfig)      ← 여기 넣으면 컨트롤러에 @PreAuthorize가 안 걸린다
자식 컨텍스트 (ServletConfig)       ← 컨트롤러·서비스가 여기 있다. 여기 넣어야 한다
```

루트에 넣으면 애노테이션은 붙어 있는데 **아무 효과가 없다.** 권한 검사가 조용히 통째로 건너뛰어진다 — `@Component` 함정과 같은 구조의 실수다.

지금 넣지 않는 이유는 role 게이팅이 필요한 API가 아직 없기 때문이다. 첫 게이팅 이슈에서 `ServletConfig`에 추가한다.

### 4.4 왜 `jwtProvider()`를 직접 호출하나

```java
@Bean
public JwtAuthenticationFilter jwtAuthenticationFilter() {
    return new JwtAuthenticationFilter(jwtProvider());   // 메서드 직접 호출
}
```

`@Bean` 메서드를 직접 부르면 새 인스턴스가 생길 것처럼 보이지만 그렇지 않다. `@Configuration` 클래스는 **CGLIB 프록시로 감싸져** 있어서, `jwtProvider()` 호출을 가로채 **이미 만들어진 싱글턴 빈**을 반환한다.

파라미터 주입(`jwtAuthenticationFilter(JwtProvider jwtProvider)`)으로 써도 결과는 같다. 플랜이 직접 호출 방식을 택했고, 빈이 6개뿐이라 의존 관계가 한눈에 보이는 이점이 있다.

### 4.5 왜 `csrf`를 끄나

CSRF 공격은 **브라우저가 쿠키를 자동으로 붙여 보내는 것**을 악용한다. 우리 인증은 `Authorization` 헤더에 토큰을 **명시적으로 실어 보내는** 방식이라, 다른 사이트에서 유도한 요청에는 토큰이 붙지 않는다. 그래서 CSRF 토큰 검사가 불필요하다.

끄지 않으면 모든 POST/PUT/DELETE가 CSRF 토큰을 요구해 403이 난다.

> 하위3/4에서 Refresh Token을 **쿠키**에 담기로 하면 이 판단을 다시 봐야 한다. 쿠키는 자동 전송되므로 CSRF 표면이 생긴다. 그때는 `SameSite` 속성이나 CSRF 토큰 도입을 검토한다.

---

## 5. 이 Task가 만들지 않은 것

**하위2 이슈는 이걸로 끝난다.** 인증 메커니즘이 완성됐고, 강제는 켜지 않았다.

| 항목 | 어디서 |
|---|---|
| `permitAll` → `authenticated()` 전환 + 공개경로 화이트리스트 | 하위3 이후, 팀 공지 후 별도 PR |
| `@EnableMethodSecurity` (**`ServletConfig`에**) | 첫 role 게이팅 이슈 |
| 로그인·회원가입 API (토큰 발급) | 하위3 |
| `CookieUtil`·`RefreshTokenStore`·`cookie.secure` | 하위3/하위4 |
| 실제 HTTP 요청 기반 통합 테스트 | `authenticated` 전환 시 |
| README 환경변수 문서 갱신 | 아래 참고 |

### 하위2 완료 기준 대조

| 완료 기준 | 담당 | 상태 |
|---|---|---|
| JWT 발급/검증·`tokenType` 계약 | Task 1 | 완료 |
| 유효 Access 토큰으로 `MemberPrincipal`+`ROLE_*` 인증 | Task 2 | 완료 |
| 토큰 없음: 익명 통과 / 만료·손상: 사유 표기 | Task 2 | 완료 |
| Refresh 토큰을 헤더로 보내면 거부 | Task 2 | 완료 |
| 인증 실패 401 JSON, 인가 실패 403 JSON | Task 3 | 완료 |
| 보안 빈이 루트 컨텍스트에 등록되고 필터체인 구성 | Task 4 | 완료 |
| 앱이 환경변수 없이도 기동(개발 기본값) | Task 1 | 완료 |
| 전체 빌드/테스트 그린 | Task 4 | 완료 (21개) |

## 남은 문서 작업

`README.md:239`가 **이제 사실과 다르다.**

> JWT 관련 환경변수는 인증 기능을 구현할 때 정의합니다. **현재 코드에서는 사용하지 않습니다.**

`SecurityConfig`가 `@Value`로 `jwt.*`를 읽으므로 코드가 사용한다. 그리고 `README:223` 환경변수 목록에 JWT 3개가 없다.

단순 누락보다 위험하다 — "사용하지 않는다"고 적혀 있으면 배포 담당자가 `JWT_SECRET`을 설정하지 않고, **앱은 정상 기동하고**, 저장소에 공개된 키로 운영이 돌아간다. 아무 경고도 나오지 않는다.
