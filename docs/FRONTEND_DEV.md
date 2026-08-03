# 프론트엔드 API 연동 안내

프론트엔드 개발자는 Swagger UI에서 API 목록과 계약을 확인하고, 실제 호출은
개발 서버 또는 Vite 프록시를 통해 수행합니다.

## 1. API 계약의 기준

백엔드 API 계약의 기준 파일은 다음 하나입니다.

```text
src/main/resources/openapi/teenymoney-api.yaml
```

배포 후 접근 주소:

```text
Swagger UI  : https://www.teenymoney.kro.kr/swagger-ui/index.html
OpenAPI YAML: https://www.teenymoney.kro.kr/api-docs/teenymoney-api.yaml
```

Swagger UI에서는 다음 정보를 확인할 수 있습니다.

- API 경로와 HTTP 메서드
- Path 및 Query 파라미터
- Request/Response 스키마
- HTTP 상태와 ErrorCode
- 요청 및 응답 예시

현재 문서는 Java 코드에서 자동 생성되지 않습니다.

> 백엔드 개발자는 도메인 API를 추가하거나 DTO와 응답 계약을 변경할 때
> `teenymoney-api.yaml`을 같은 작업에서 반드시 갱신해야 합니다.
> YAML 갱신 전에는 프론트엔드에 API 구현 완료를 알리지 않습니다.

## 2. Vite 프록시

프론트엔드 코드에서는 API 서버 주소를 직접 붙이지 않고 상대 경로를 사용합니다.

```javascript
fetch('/api/v1/health')
```

개발 환경의 `vite.config.js` 예시:

```javascript
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'https://www.teenymoney.kro.kr',
        changeOrigin: true,
        cookieDomainRewrite: 'localhost',
      },
    },
  },
})
```

이 방식이면 프론트엔드 코드는 로컬과 배포 환경에서 동일한 `/api/v1/...` 경로를
사용할 수 있습니다.

## 3. Swagger에서 호출

Swagger UI의 서버 기본값은 현재 Swagger가 열린 서버입니다.

```yaml
servers:
  - url: /
```

따라서 EC2 Swagger에서 `Try it out`을 실행하면 EC2 Backend를 호출하고, 로컬 Swagger에서
실행하면 로컬 Backend를 호출합니다. 필요하면 Swagger의 서버 선택 메뉴에서 로컬 또는
개발 통합 서버를 명시적으로 선택할 수 있습니다.

## 4. Postman

Postman에서 다음 URL을 Import하면 OpenAPI 기준의 요청 목록을 만들 수 있습니다.

```text
https://www.teenymoney.kro.kr/api-docs/teenymoney-api.yaml
```

Postman 환경은 최소 다음처럼 구분합니다.

```text
Local
  baseUrl = http://localhost:8080

Development
  baseUrl = https://www.teenymoney.kro.kr
```

공유 환경에는 비밀번호, 운영 토큰, DB 정보 같은 비밀값을 저장하지 않습니다.

Postman Collection은 호출과 시나리오 테스트 도구입니다. API 계약의 기준은 Postman
Collection이 아니라 `teenymoney-api.yaml`입니다.

## 5. 백엔드와 프론트엔드의 변경 절차

백엔드 개발자:

```text
1. Controller와 DTO 구현
2. Service, Mapper, VO 구현
3. teenymoney-api.yaml의 paths와 schemas 갱신
4. Swagger UI에서 표시 확인
5. 실제 API 호출 확인
6. 프론트엔드에 변경 내용과 배포 완료 공유
```

프론트엔드 개발자:

```text
1. Swagger UI에서 최신 계약 확인
2. 요청 및 응답 예시에 맞춰 연동
3. 필요하면 OpenAPI를 Postman으로 다시 Import
4. 실제 개발 서버 호출로 최종 확인
```

API 계약이 코드와 다르면 구두 설명만으로 맞추지 말고 OpenAPI YAML을 먼저 수정합니다.

## 6. 아직 구현되지 않은 API

**현재 백엔드는 공개 경로를 제외한 모든 요청에 인증을 요구합니다.** 그 밖의 경로는
**존재하지 않는 경로여도 404가 아니라 401**이 돌아오므로, 401을 "API가 아직 없다"는
신호로 읽으면 안 됩니다. 공개 경로 전체 목록과 이유는
[README의 "인가 규칙"](../README.md#인가-규칙)에 있습니다.

지금 호출할 수 있는 API:

```text
POST /api/v1/auth/phone-verification/send
GET  /api/v1/auth/check-email?email=
POST /api/v1/auth/signup
POST /api/v1/auth/login
GET  /api/v1/members/me                      (Bearer 필요)
GET  /api/v1/health, /api/v1/health/db
```

아직 없는 것: **재발급·로그아웃**(구현 중), 가족 연동, 지갑, 거래내역, 소셜 로그인.

소셜 로그인은 이번 범위에 없습니다. 로그인 화면의 구글 버튼은 비활성 처리하거나
숨겨 주세요.

구현 전 화면 개발이 필요하면 아래 Mock을 사용합니다.

구현 전 화면 개발이 필요하면 OpenAPI에 확정된 요청·응답 예시를 먼저 정의하고 Postman
Mock Server 또는 MSW를 사용할 수 있습니다. Mock 응답 역시 OpenAPI 계약과 일치해야
합니다.

## 7. EC2 공개 조건

Nginx는 다음 경로를 Tomcat으로 전달해야 합니다.

```text
/api/
/swagger-ui/
/api-docs/
```

배포 후 프론트엔드에 공유하기 전에 다음 주소가 외부에서 HTTP 200인지 확인합니다.

```text
https://www.teenymoney.kro.kr/api/v1/health
https://www.teenymoney.kro.kr/swagger-ui/index.html
https://www.teenymoney.kro.kr/api-docs/teenymoney-api.yaml
```

## 8. 명명 규칙

계약이 어긋나는 대부분은 이름 때문입니다. 아래 규칙은 예외 없이 적용됩니다.

| 대상 | 규칙 | 예 |
|---|---|---|
| URL 경로 | 소문자 + 하이픈 | `/auth/check-email`, `/auth/phone-verification/send` |
| 경로 접두사 | 전부 `/api/v1` | `/api/v1/members/me` |
| JSON 필드 | camelCase | `birthDate`, `phoneNumber`, `accessToken` |
| 날짜 | `yyyy-MM-dd` 문자열 | `"2013-05-20"` |
| enum 값 | 대문자 + 밑줄 | `PARENT`, `CHILD`, `ACTIVE` |
| boolean 필드 | `is`/`has` 접두사 없음 | `available` |
| ErrorCode | `도메인_사유` 대문자 | `AUTH_DUPLICATE_EMAIL` |

### 8.1 요청 필드 이름은 화면 상태 이름과 다릅니다

프론트엔드의 폼 변수명은 자유지만, **전송 직전에 계약 이름으로 매핑**해야 합니다.
객체를 그대로 펼쳐 보내면 400이 납니다.

```javascript
await api.post('/api/v1/auth/signup', { ...form })   // 실패
```

`Signup.vue` 기준 현재 어긋나 있는 것:

| 화면 상태 | 요청 필드 |
|---|---|
| `form.birthdate` | `birthDate` |
| `form.phone` | `phoneNumber` |
| `form.passwordConfirm` | **보내지 않습니다** (화면에서만 검증) |

### 8.2 서버가 정하는 값은 보내지 않습니다

| 값 | 이유 |
|---|---|
| `role` | `birthDate`로 서버가 판정합니다. 만 **7~18세만 `CHILD`**, 그 밖은 전부 `PARENT` |
| `teenyScore` | 서버가 초기값을 넣습니다 |
| `memberId` | 토큰에서 꺼냅니다. 요청 본문이나 쿼리로 받지 않습니다 |

`phoneNumber`는 하이픈이 있어도 없어도 됩니다. 서버가 숫자만 남겨 저장하고,
응답도 숫자만 내려갑니다. 화면 표시 형식은 프론트엔드가 정합니다.

### 8.3 응답 껍데기는 항상 같습니다

```json
{"success": true,  "code": "OK", "message": "성공", "data": { }}
{"success": false, "code": "AUTH_INVALID_CREDENTIALS", "message": "이메일 또는 비밀번호가 올바르지 않습니다.", "data": null}
```

- 분기는 **`code`로** 합니다. `message`는 문구가 바뀔 수 있습니다
- `message`는 사용자에게 그대로 노출해도 되도록 작성합니다. 프론트엔드가 문구를
  다시 만들 필요가 없습니다
- 검증 실패(`COMMON_INVALID_INPUT`)일 때만 `data`가 **필드별 사유 맵**입니다.
  키 이름은 요청 필드 이름과 같으므로 입력란에 그대로 연결할 수 있습니다

### 8.4 상태 코드별 처리

| 상태 | code | 처리 |
|---|---|---|
| 400 | `COMMON_INVALID_INPUT` | `data`의 필드별 사유를 입력란에 표시 |
| 401 | `AUTH_TOKEN_EXPIRED` | 재발급 시도 → 실패하면 로그인 화면 |
| 401 | 그 외 | 로그인 화면 |
| **403** | `AUTH_INACTIVE_MEMBER` | **로그인 화면으로 보내지 않습니다.** 비밀번호는 맞았으므로 다시 로그인시키면 무한 반복됩니다 |
| 409 | `AUTH_DUPLICATE_EMAIL` / `AUTH_DUPLICATE_PHONE_NUMBER` | 해당 입력란에 표시 |
| 429 | `AUTH_SMS_TOO_MANY_REQUESTS` / `AUTH_VERIFICATION_TOO_MANY_ATTEMPTS` | 재시도 안내 |

### 8.5 Refresh Token 쿠키

로그인 응답의 `Set-Cookie`는 다음 속성으로 내려갑니다.

```text
refreshToken=...; HttpOnly; SameSite=Lax; Path=/api/v1/auth
```

- **JS로 읽을 수 없습니다.** 쿠키 존재 여부로 로그인 상태를 판단하지 마세요
- `Path`가 `/api/v1/auth`이므로 브라우저는 **`/api/v1/auth/*` 요청에만** 이 쿠키를
  보냅니다. 재발급은 반드시 `POST /api/v1/auth/reissue`로 호출해야 합니다
- Access Token을 메모리(Pinia)에 두면 새로고침 시 사라집니다. 앱 시작 시 재발급을
  한 번 호출해 복구하는 흐름이 필요합니다

### 8.6 휴대폰 인증 (개발 중 한정)

실제 SMS 발송은 아직 연결되지 않았습니다. 개발 환경에서는 인증번호가
**`123456`으로 고정**되어 있습니다. 발송 API는 호출해도 되고, 건너뛰고 바로
`123456`을 넣어도 가입이 진행됩니다.

운영에서는 이 고정값이 동작하지 않습니다.
