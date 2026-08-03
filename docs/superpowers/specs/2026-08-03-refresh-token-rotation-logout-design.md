# Refresh Token Rotation and Logout Design

## 목표

로그인에서 발급한 Refresh Token을 안전하게 재발급·Rotation하고, 로그아웃 시 해당
계정의 Refresh Token과 기존 Access Token을 모두 즉시 무효화한다. 다른 계정의 토큰은
영향받지 않으며, 로그아웃 대상 토큰이 이미 없어도 성공으로 처리한다.

## 현재 구조와 범위

- Access Token은 응답 본문으로 전달하고 API 요청의 `Authorization: Bearer` 헤더로 받는다.
- Refresh Token은 `refreshToken` HttpOnly Cookie와 Redis `refresh:{memberId}`에 저장한다.
- 계정당 Refresh Token은 하나만 유지한다. 다중 기기별 Refresh 세션 관리는 이번 범위가 아니다.
- DB 스키마와 외부 라이브러리는 추가하지 않는다.
- 프론트의 Access Token 저장 위치는 별도 협의 사항이다. 권장안은 비영속 Pinia 메모리다.
- 기존 로컬·EC2 환경변수 계약은 변경하지 않는다.

## 토큰 데이터

### Access Token

Access Token 원문은 Redis에 저장하지 않는다.

| Claim | 값 |
| --- | --- |
| `sub` | 회원 ID 문자열 |
| `role` | 현재 회원 역할 |
| `tokenType` | `ACCESS` |
| `authGeneration` | 계정 인증 세대 UUID |
| `iat` | 발급 시각 |
| `exp` | 만료 시각, 기본 30분 |

### Refresh Token

| Claim | 값 |
| --- | --- |
| `sub` | 회원 ID 문자열 |
| `tokenType` | `REFRESH` |
| `authGeneration` | Access Token과 같은 계정 인증 세대 UUID |
| `iat` | 발급 시각 |
| `exp` | 만료 시각, 기본 14일 |

Refresh Token에는 `role`을 넣지 않는다. 재발급 시 DB에서 최신 역할과 회원 상태를 조회한다.

## Redis 데이터

| Key | Value | TTL |
| --- | --- | --- |
| `refresh:{memberId}` | 현재 Refresh Token 원문 | Refresh 만료시간 |
| `auth:generation:{memberId}` | 무작위 UUID | Refresh 만료시간 |

`authGeneration`은 비밀값이 아니라 계정의 현재 인증 세대를 구분하는 식별자다. 최초 로그인에서
키가 없으면 UUID를 원자적으로 생성한다. 로그인과 재발급 시 TTL을 Refresh 만료시간으로
갱신한다. Rotation에서는 UUID를 바꾸지 않는다.

Redis 키가 사라지거나 JWT의 값과 다르면 인증을 거부한다. Redis 초기화 후 새 로그인에서는
새 UUID가 생성되므로 과거 Access Token이 다시 유효해지지 않는다.

## API 계약

### `POST /api/v1/auth/reissue`

- Request body는 없다.
- `refreshToken` Cookie와 CSRF 헤더가 필요하다.
- 성공 응답 본문에는 새 Access Token만 포함한다.
- 새 Refresh Token은 `Set-Cookie`로 전달한다.

성공 흐름:

1. Refresh Cookie를 읽는다.
2. JWT 서명, 만료, `tokenType=REFRESH`, 회원 ID와 `authGeneration`을 검증한다.
3. Redis의 인증 세대와 일치하는지 확인한다.
4. DB에서 회원을 조회하고 `ACTIVE` 상태와 최신 `role`을 확인한다.
5. 새 Access/Refresh Token을 생성한다.
6. Redis Lua에서 저장된 Refresh Token이 요청 토큰과 같을 때만 새 토큰으로 교체한다.
7. 새 Access Token을 응답하고 Refresh Cookie를 교체한다.

Redis 비교·교체는 한 명령으로 처리한다. 동시에 같은 이전 Refresh Token으로 재발급을 요청해도
하나만 성공하며, 이미 Rotation된 토큰의 재사용은 `401 AUTH_TOKEN_INVALID`이다.

### `POST /api/v1/auth/logout`

- CSRF 헤더가 필요하다.
- 유효한 Access Bearer Token을 우선 사용해 회원을 식별하고, 없으면 Refresh Cookie를 사용한다.
- 식별된 회원의 `refresh:{memberId}`와 `auth:generation:{memberId}`를 삭제한다.
- 항상 Refresh Cookie 만료 헤더를 보낸다.
- 토큰이나 Redis 키가 이미 없거나 유효하지 않아 회원을 식별할 수 없어도 `200 OK`다.
- 회원을 식별했지만 Redis 장애로 삭제를 보장할 수 없으면 Cookie는 지우고 `503`을 반환한다.

인증 세대 키가 삭제되면 해당 계정의 기존 Access Token은 모두 다음 요청부터 거부된다. 다른
계정의 Redis 키는 변경하지 않는다.

## Access Token 요청 검증

기존 JWT 필터 검증 뒤 다음 단계를 추가한다.

1. 서명, 만료, `tokenType=ACCESS`, `sub`, `role`, `authGeneration`을 검증한다.
2. Redis `auth:generation:{memberId}`를 조회한다.
3. Redis 값과 JWT `authGeneration`이 같을 때만 `SecurityContext`를 채운다.
4. 키가 없거나 값이 다르면 `401 AUTH_TOKEN_INVALID`이다.
5. Redis 장애면 인증을 허용하지 않고 `503 COMMON_SERVICE_UNAVAILABLE`을 반환한다.

Access Token 원문이나 차단목록은 저장하지 않는다. 보호 API 요청당 Redis 조회 한 번이 추가된다.

## CSRF 방어

`SameSite`는 보조 방어이며 단독 방어로 사용하지 않는다.

- Refresh Cookie는 `HttpOnly`, 운영 `Secure`, `SameSite=Strict`, Path `/api/v1/auth`를 사용한다.
- 상태 변경 엔드포인트는 `POST`만 사용한다.
- Spring Security의 Cookie 기반 CSRF 토큰을 사용한다.
- CSRF Cookie 이름은 `XSRF-TOKEN`, 요청 헤더는 `X-XSRF-TOKEN`이다.
- 공개 `GET /api/v1/auth/csrf`가 CSRF 토큰을 발급한다.
- `POST /api/v1/auth/login`, `/reissue`, `/logout`은 CSRF 헤더를 검증한다.
- Access Bearer Token만 사용하는 일반 API는 브라우저가 인증값을 자동 첨부하지 않으므로 이
  Cookie 인증 엔드포인트 범위에 포함하지 않는다.
- 운영은 동일 출처를 기본으로 하고, 별도 CORS가 필요하면 credential 허용과 함께 정확한
  프론트 Origin만 허용한다. 와일드카드 Origin은 사용하지 않는다.

CSRF 실패는 기존 JSON 오류 형식을 유지하며 `403 AUTH_FORBIDDEN`으로 응답한다.

## 오류 처리

| 상황 | 응답 |
| --- | --- |
| Refresh Cookie 없음·형식 오류·타입 오류·Redis 불일치 | `401 AUTH_TOKEN_INVALID` |
| Refresh Token 만료 | `401 AUTH_TOKEN_EXPIRED` |
| 비활성 회원 | `403 AUTH_INACTIVE_MEMBER` |
| CSRF 불일치 | `403 AUTH_FORBIDDEN` |
| Redis 장애 | `503 COMMON_SERVICE_UNAVAILABLE` |
| 로그아웃 대상 토큰·키 없음 | `200 OK` |

오류 응답에 토큰 원문, Redis 값 또는 회원 존재 여부를 노출하지 않는다.

## 프론트 연동 계약

- 로그인과 재발급 응답의 Access Token을 `Authorization: Bearer`로 전송한다.
- Access Token 저장 방식은 FE가 확정한다. 권장안은 비영속 Pinia 메모리이며
  `localStorage`와 `sessionStorage`는 XSS에서 읽힐 수 있다.
- 비영속 Pinia를 선택하면 앱 시작 시 `/auth/csrf` 후 `/auth/reissue`를 호출한다.
- 동시 `401`에서는 재발급 요청을 하나만 수행하고 원래 요청을 한 번만 재시도한다.
- 재발급 실패 시 Access Token과 사용자 상태를 지우고 로그인 화면으로 이동한다.
- 로그아웃 성공 시 상태 코드와 무관하게 FE가 보관한 Access Token을 삭제한다.

## 테스트 기준

- JwtProvider가 두 토큰에 같은 `authGeneration`을 넣고 타입과 역할을 구분한다.
- 로그인은 인증 세대를 생성·재사용하고 Redis TTL을 갱신한다.
- 정상 재발급은 Access/Refresh를 새로 발급하고 이전 Refresh를 재사용할 수 없다.
- 동시 Rotation에서는 하나만 성공한다.
- 잘못된 타입, 만료, 위조, Redis 불일치, 비활성 회원을 각 상태 코드로 구분한다.
- 로그아웃은 Refresh와 인증 세대를 삭제하고 Cookie를 만료한다.
- 로그아웃 이후 같은 계정의 기존 Access Token은 모두 실패하고 다른 계정 토큰은 성공한다.
- 토큰이 없는 로그아웃은 `200 OK`다.
- CSRF 헤더 없는 로그인·재발급·로그아웃은 `403`, 올바른 헤더는 정상 처리된다.
- 전체 JUnit 테스트와 `war` 빌드가 통과한다.
