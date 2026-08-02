# TeenyMoney Backend

부모와 자녀가 함께 만들어 가는 가족 금융 교육 서비스
**티니머니(TeenyMoney)**의 백엔드 저장소입니다.

부모의 일방적인 소비 통제보다 자녀가 퀘스트, 금융 활동, 소비 경험을 통해
신뢰를 쌓고 점진적으로 금융 자율성을 얻는 것을 목표로 합니다.

## 프로젝트 정보

- 팀명: Teenypin
- 개발 기간: 2026년 7월 ~ 2026년 8월
- 구성: Backend 4명, Frontend 2명
- API 기본 경로: `/api/v1`
- 패키징: WAR (`ROOT.war`)
- 배포 구성: AWS EC2, Nginx, Tomcat 9, Docker MySQL, Docker Redis

## 현재 구현 상태

현재 저장소에서 실행 가능한 범위입니다.

- Java 17 및 Spring MVC 기반 WAR 프로젝트
- 공통 API 응답과 전역 예외 처리
- MyBatis 및 MySQL 연결 설정
- Redis 및 Spring Security 기본 설정
- JWT 인증 파이프라인 (토큰 발급·검증, 인증 필터, 401/403 응답)
- 공개 경로를 제외한 전 요청 인증 강제 (`anyRequest().authenticated()`)
- 애플리케이션 상태 확인 API
- 데이터베이스 연결 확인 API
- OpenAPI 3.0 명세와 Swagger UI
- GitHub Actions 테스트 및 WAR 빌드 CI

회원, 인증, 가족 연결, 지갑, 결제, 금융상품, 퀘스트, 알림 도메인은 구현
예정입니다.

## 인가 규칙

**공개 경로를 제외한 모든 요청은 인증이 필요합니다.** 이 규칙은 이미 적용되어
있습니다(`SecurityConfig`의 `anyRequest().authenticated()`).

공개 경로의 유일한 기준은 `SecurityConfig.PUBLIC_ENDPOINTS`입니다.

| 경로 | 공개 이유 |
| --- | --- |
| `/api/v1/auth/signup` | 회원가입 — 토큰이 있을 수 없다 |
| `/api/v1/auth/login` | 로그인 — 토큰을 받으러 오는 곳 |
| `/api/v1/auth/reissue` | 재발급 — Access가 만료된 상태로 온다 |
| `/api/v1/health`, `/api/v1/health/**` | 모니터링이 토큰 없이 호출 |
| `/swagger-ui/**`, `/api-docs/**` | API 문서 |

유효한 Access Token을 보내면 인증 정보가 채워지고 컨트롤러가
`@AuthenticationPrincipal MemberPrincipal`로 받습니다.

**단, 토큰을 발급하는 로그인 API는 아직 없습니다**(인증 API는 구현 중). 그래서
현재 실제로 호출할 수 있는 것은 위 표의 `health` 2개와 문서 경로뿐이고, 그 밖의
경로는 **존재하지 않는 경로여도 404가 아니라 401**이 돌아옵니다. 인가 판단이
DispatcherServlet보다 먼저 끝나기 때문입니다.

`auth` 3개 경로는 화이트리스트에만 등록되어 있고 처리할 컨트롤러가 없어 호출하면
401이 아니라 404입니다. 프론트엔드 연동은 인증 API 구현 이후에 시작합니다.

수동 확인용 토큰이 필요하면 `TokenPrinterTest`로 발급합니다. 출력된 토큰을
`Authorization: Bearer <토큰>` 헤더에 넣어 사용합니다.

```bash
./gradlew test --tests "*TokenPrinterTest" --rerun-tasks -i
```

토큰을 발급하는 개발용 엔드포인트는 배포물에 백도어가 되므로 만들지 않습니다.
`src/test`에 두면 WAR에 포함되지 않습니다.

`JWT_SECRET`은 아직 필수가 아닙니다. 미설정 시 저장소에 공개된 개발 기본값으로
서명되며 앱은 정상 기동합니다. **배포 환경에서는 반드시 override합니다**
(아래 [환경변수](#환경변수) 참고).

인증 파이프라인의 설계 근거는 [JWT·Spring Security 구현 플랜](docs/jwt-security-pipeline.md)을 참고합니다.

## 기술 스택

### Backend

- Java 17
- Spring Framework 5.3.37
- Spring MVC
- Spring Security 5.8.16
- MyBatis 3.4.6
- MySQL Connector/J 8.1.0
- HikariCP
- Spring Data Redis, Lettuce
- Gradle 8.8
- Lombok 1.18.30
- Jackson 2.15.4
- Log4j2 2.18.0
- Swagger UI 5.31.0

### Infrastructure

- AWS EC2 Ubuntu 24.04 LTS
- Nginx
- Apache Tomcat 9
- Docker MySQL 8
- Docker Redis
- HTTPS
- GitHub Actions

## 시스템 구성

```text
사용자 브라우저
       |
       v
     Nginx
   /        \
Vue 정적 파일  /api, /swagger-ui, /api-docs
                  |
                  v
               Tomcat 9
                  |
             Spring MVC WAR
               /       \
            MySQL     Redis
```

Nginx는 프론트엔드 정적 파일을 제공하고 백엔드 요청을 Tomcat의
`ROOT.war` 애플리케이션으로 전달합니다.

## 프로젝트 구조

```text
.
├── .github/
│   ├── ISSUE_TEMPLATE/
│   ├── pull_request_template.md
│   └── workflows/
│       ├── ci.yml
│       └── README.md
├── docs/
│   ├── FRONTEND_DEV.md
│   ├── LOCAL_TEST.md
│   └── PROJECT_STRUCTURE.md
├── sql/
│   ├── schema/
│   ├── migration/
│   ├── seed/
│   └── README.md
├── src/
│   ├── main/
│   │   ├── java/com/teenyfin/teenymoney/
│   │   │   ├── config/
│   │   │   ├── domain/
│   │   │   └── global/
│   │   │       ├── auth/
│   │   │       ├── exception/
│   │   │       ├── health/
│   │   │       │   ├── controller/
│   │   │       │   ├── dto/response/
│   │   │       │   ├── mapper/
│   │   │       │   ├── service/
│   │   │       │   └── vo/
│   │   │       ├── idempotency/
│   │   │       ├── response/
│   │   │       └── security/
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── mybatis-config.xml
│   │       ├── openapi/teenymoney-api.yaml
│   │       ├── swagger-ui/swagger-initializer.js
│   │       └── com/teenyfin/teenymoney/
│   └── test/java/com/teenyfin/teenymoney/
├── build.gradle
├── settings.gradle
├── gradlew
└── gradlew.bat
```

### 최상위 패키지 역할

| 패키지 | 역할 |
| --- | --- |
| `config` | Spring Root/Servlet Context, MyBatis, Redis, Security 등 애플리케이션 구성 |
| `global` | 여러 도메인이 공유하는 응답, 예외, 인증, 보안, 상태 확인 기능 |
| `domain` | 회원, 지갑, 결제 등 비즈니스 기능별 구현 |

`auth`, `idempotency`, `security` 패키지는 향후 공통 기능을 위한 확장 위치입니다.
새로운 비즈니스 도메인은 다음 전체 경로와 구조를 기본으로 사용합니다.

```text
src/main/java/com/teenyfin/teenymoney/domain/<feature>/
├── controller/
├── dto/
│   ├── request/
│   └── response/
├── service/
├── mapper/
└── vo/
```

MyBatis Mapper XML은 Java 패키지와 대응하도록
`src/main/resources/com/teenyfin/teenymoney/.../mapper/` 아래에 둡니다.

상세 기준은 [프로젝트 구조 문서](docs/PROJECT_STRUCTURE.md)를 참고합니다.

## 공통 API 응답

모든 REST API 응답은 다음 형태를 사용합니다.

```json
{
  "success": true,
  "code": "OK",
  "message": "성공",
  "data": {}
}
```

실패 응답은 `success=false`로 반환하며, `code`에는 서버에서 정의한
에러 코드 값을 사용합니다. 내부 예외 메시지, SQL, 서버 경로는
응답에 노출하지 않고 서버 로그에만 기록합니다.

에러 코드는 `ErrorCode` 인터페이스(`getStatus`, `getMessage`, `getCode`)로 정의합니다.
공통 인프라 오류와 시큐리티 교차 관심사(`AUTH_UNAUTHORIZED`, `AUTH_FORBIDDEN`)는
`CommonErrorCode`에서 관리하고, 도메인 업무 오류는 도메인별 enum(예: `AuthErrorCode`)이
`ErrorCode`를 구현해 `domain/<도메인>/exception`에 둡니다. 각 enum의 `getCode()`는
`name()`을 반환하며, 공통 파일(`CommonErrorCode`, `GlobalExceptionAdvice`, `ApiResponse`)에
도메인 코드를 추가하지 않습니다.

성공 응답의 `code`는 공통으로 `OK`를 사용합니다. 도메인별 성공 코드는 별도
팀 결정 없이 추가하지 않습니다.

애플리케이션 상태 확인 응답 예시는 다음과 같습니다.

```json
{
  "success": true,
  "code": "OK",
  "message": "성공",
  "data": {
    "status": "UP",
    "time": "2026-07-28 10:20:00"
  }
}
```

공통 응답 메타데이터로 `timestamp`를 추가하지 않습니다. 상태 확인 API의
`data.time`은 서버 시간 확인을 위한 해당 API 전용 응답 필드입니다.

## 로컬 개발 환경

### 필수 항목

- JDK 17
- MySQL 8
- Git
- Tomcat 9

Redis를 사용하는 기능을 개발할 때는 로컬 Redis도 실행해야 합니다. IntelliJ IDEA,
MySQL Workbench 또는 DataGrip, Postman은 선택 도구입니다.

### 저장소 복제

```bash
git clone https://github.com/KB-PJT-CLASS22-TEAM4/TeenyMoney-Backend.git
cd TeenyMoney-Backend
```

### 환경변수

현재 애플리케이션이 사용하는 환경변수입니다.

```text
DB_DRIVER=net.sf.log4jdbc.sql.jdbcapi.DriverSpy
DB_URL=jdbc:log4jdbc:mysql://localhost:3306/teenymoney?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=<LOCAL_MYSQL_USERNAME>
DB_PASSWORD=<LOCAL_MYSQL_PASSWORD>
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=<openssl rand -base64 32 으로 생성한 값>
JWT_ACCESS_EXPIRATION_MS=1800000
JWT_REFRESH_EXPIRATION_MS=1209600000
```

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`는 필수입니다. 실제 값은 IntelliJ 실행
구성, 운영체제 환경변수 또는 Tomcat 실행 환경에 주입하며 저장소에 작성하지 않습니다.

`JWT_SECRET`은 로컬 개발용 기본값이 `application.properties`에 있어 **설정하지
않아도 앱이 정상 기동합니다.** 그래서 누락을 알아차릴 수 없습니다. 이 기본값은
저장소에 공개되어 있으므로 **배포 환경에서는 반드시 override해야 합니다.** 설정하지
않으면 경고 없이 공개된 키로 토큰이 서명되고, 저장소를 볼 수 있는 누구나 유효한
토큰을 위조할 수 있습니다.

```bash
openssl rand -base64 32      # Base64로 인코딩된 32바이트 키를 생성한다
```

`JWT_ACCESS_EXPIRATION_MS`(기본 30분)와 `JWT_REFRESH_EXPIRATION_MS`(기본 14일)는
선택 항목입니다. 기본값을 그대로 사용하면 됩니다.

인스턴스를 여러 대로 늘릴 경우 **모든 인스턴스가 같은 `JWT_SECRET`을 가져야
합니다.** A 서버가 발급한 토큰을 B 서버가 검증하기 때문입니다. 또 `exp` 검증에
허용 오차가 없으므로 서버 시각(NTP)이 동기화되어 있어야 합니다.

자세한 실행 방법은 [로컬 테스트 문서](docs/LOCAL_TEST.md)를 참고합니다.

## 빌드 및 테스트

Windows PowerShell:

```powershell
.\gradlew.bat clean test war
```

macOS 또는 Linux:

```bash
./gradlew clean test war
```

빌드 결과는 다음 위치에 생성됩니다.

```text
build/libs/ROOT.war
```

IntelliJ Tomcat으로 실행할 때는 애플리케이션 컨텍스트 경로를 `/`로 설정해야
아래 주소를 그대로 사용할 수 있습니다.

## API 및 문서

### 로컬

| 구분 | URL |
| --- | --- |
| 애플리케이션 상태 | `http://localhost:8080/api/v1/health` |
| 데이터베이스 상태 | `http://localhost:8080/api/v1/health/db` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI YAML | `http://localhost:8080/api-docs/teenymoney-api.yaml` |

### 개발 통합 서버

| 구분 | URL |
| --- | --- |
| 서비스 | `https://www.teenymoney.kro.kr` |
| 애플리케이션 상태 | `https://www.teenymoney.kro.kr/api/v1/health` |
| 데이터베이스 상태 | `https://www.teenymoney.kro.kr/api/v1/health/db` |
| Swagger UI | `https://www.teenymoney.kro.kr/swagger-ui/index.html` |
| OpenAPI YAML | `https://www.teenymoney.kro.kr/api-docs/teenymoney-api.yaml` |

OpenAPI 원본은 다음 파일입니다.

```text
src/main/resources/openapi/teenymoney-api.yaml
```

API를 추가하거나 변경할 때 Controller, DTO, Service, Mapper와 함께 이 파일도
반드시 갱신합니다. DB를 사용하지 않는 API는 Mapper와 VO가 필요하지 않습니다.

프론트엔드 연동 기준은 [프론트엔드 개발 문서](docs/FRONTEND_DEV.md)를 참고합니다.

## 데이터베이스 개발 원칙

- 개인 기능 개발과 SQL 실험은 각자의 로컬 MySQL에서 진행합니다.
- EC2 MySQL은 팀 공용 통합 테스트 환경으로 사용합니다.
- 검토되지 않은 DDL이나 테스트 데이터를 EC2에 직접 반영하지 않습니다.
- 실제 개인정보, 운영 계정, 비밀번호를 SQL이나 문서에 기록하지 않습니다.

현재 환경 구분은 다음과 같습니다.

| 환경 | 목적 | 데이터베이스 |
| --- | --- | --- |
| Local | 개인 기능 개발 및 반복 테스트 | 개인 로컬 MySQL |
| EC2 Integration | 프론트엔드·백엔드 통합 테스트, 멘토링, 시연 | EC2 Docker MySQL |
| Production | 현재 별도 구성 없음 | 추후 결정 |

현재 EC2는 실제 운영 환경이 아니라 팀 개발 통합 환경입니다.

현재 Flyway는 도입되지 않았습니다. Flyway 도입 전까지 DDL과 테스트 데이터는
다음 위치에서 관리합니다.

```text
sql/
├── schema/       # 현재 전체 스키마
├── migration/    # 순서대로 적용할 변경 SQL
└── seed/         # 로컬 전용 테스트 데이터
```

- EC2에는 Pull Request 검토가 끝난 SQL만 반영합니다.
- EC2에 적용한 migration 파일은 수정하지 않고 새 파일을 추가합니다.
- schema 파일은 승인된 migration 반영 후 현재 구조와 일치하도록 갱신합니다.
- seed에는 실제 개인정보와 공용 환경용 자격증명을 넣지 않습니다.
- 현재 SQL은 자동 적용되지 않으며 담당자가 적용 대상 환경을 확인한 뒤 실행합니다.

세부 규칙은 [SQL 변경 관리 문서](sql/README.md)를 참고합니다.

## CI

[GitHub Actions CI](.github/workflows/ci.yml)는 `dev`, `main` 대상 Pull Request와
두 브랜치의 push에서 실행됩니다.

CI는 다음 항목을 검사합니다.

- 필수 Gradle, OpenAPI, GitHub 템플릿 파일
- SQL 변경 관리 문서
- `.idea`, 실제 `.env`, 로컬 설정, 키 및 인증서 파일 커밋 여부
- DB 접속 정보의 환경변수 사용 여부
- Java 17 기반 전체 테스트
- `ROOT.war` 생성

CI는 실제 MySQL, Redis 또는 EC2에 연결하지 않으며 배포도 수행하지 않습니다.
브랜치 보호 규칙에서는 `repo-policy`, `backend-build`를 Required Check로
설정합니다.

## 개발 예정 범위

- 회원가입과 로그인 (JWT 토큰 발급) — **진행 중**
- Redis Refresh Token 관리와 토큰 재발급 — **진행 중**
- `JWT_SECRET` 미설정 시 기동 실패 처리 (현재는 개발 기본값으로 기동)
- 가족 연결
- 지갑, 거래 원장, 용돈
- 결제와 업종별 결제 정책
- 티니점수와 신뢰도
- 예금, 적금 등 금융상품
- 퀘스트와 보상
- 알림
- 역할 기반 인가
- EC2 자동 배포

예정 기능의 API, 보안 정책, 데이터 모델은 구현과 리뷰를 거친 뒤 확정합니다.
구현되지 않은 정책을 현재 동작으로 간주하지 않습니다.

## Git 작업 규칙

- 초기 저장소 등록 이후 `main`, `dev`에는 직접 push하지 않습니다.
- Issue 단위로 작업 브랜치를 생성합니다.
- Pull Request에서 CI와 코드 리뷰를 통과한 뒤 병합합니다.
- API 변경 시 OpenAPI YAML과 관련 문서를 함께 갱신합니다.
- 비밀번호, 토큰, SSH 키, 실제 개인정보를 커밋하지 않습니다.

Issue와 Pull Request의 메타데이터는 다음 기준으로 관리합니다.

- Issue 제목에는 접두어 없이 실제 작업 내용만 작성합니다.
- Backend와 Frontend 작업은 Repository로 구분합니다.
- 작업 종류는 Organization Issue Type으로 관리합니다.
- 우선순위와 작업량은 Organization Issue Field의 `Priority`, `Effort`로 관리합니다.
- 일정은 `Start date`, `Target date`로 관리합니다.
- 상위 작업 관계는 Parent issue와 Sub-issue로 연결합니다.
- `domain:*` Label은 업무 기능 영역, `area:*` Label은 기술 작업 영역을 나타냅니다.
- 논의가 필요한 작업은 `needs: discussion`, 진행이 차단된 작업은 `status: blocked` Label을 사용합니다.

브랜치 이름은 `<이슈번호>-<타입>-<담당자이니셜>-<작업요약>` 형식을 사용합니다.

```text
7-chore-psh-github-templates
```

커밋 메시지 예시:

```text
feat: 회원가입 API 구현
fix: 토큰 만료 검증 오류 수정
refactor: 회원 조회 로직 분리
docs: 로컬 실행 방법 추가
test: 로그인 서비스 테스트 추가
chore: Redis 의존성 추가
```

Pull Request 제목은 `type(scope): 변경 내용` 형식을 사용합니다. 허용하는 `type`은
`feat`, `fix`, `refactor`, `test`, `docs`, `chore`이며, `scope`에는 `auth`,
`member`, `wallet`, `common`, `github`, `infra` 등 변경 영역을 작성합니다.

## 보안 주의사항

- 비밀번호, JWT Secret, DB 자격증명, SSH 키를 저장소와 로그에 남기지 않습니다.
  단 `application.properties`의 `jwt.secret` 기본값은 **로컬 개발 전용이며 비밀이
  아닙니다**(환경변수 없이도 앱이 기동하도록 둔 값). 배포 환경에서는 `JWT_SECRET`으로
  반드시 override합니다.
- 토큰 값과 서명 키를 로그에 출력하지 않습니다. 인증 필터에 로거를 두지 않은 이유입니다.
- 실제 개인정보를 테스트 데이터로 사용하지 않습니다.
- 클라이언트가 전달한 회원 ID와 권한을 그대로 신뢰하지 않습니다.
- 금융 요청에는 트랜잭션과 멱등성을 적용합니다.
- `/api/v1/health/db`는 개발 통합 확인용입니다. 운영 환경에서는 공개 범위와
  응답 정보 수준을 다시 검토해야 합니다. 현재 응답은 선택된 DB 이름을 포함하며,
  DB URL, 사용자명, 내부 IP, SQL 예외, 스택 트레이스는 반환하지 않습니다.

## 문서

- [프로젝트 구조와 구현 규칙](docs/PROJECT_STRUCTURE.md)
- [로컬 테스트 및 Tomcat 실행](docs/LOCAL_TEST.md)
- [프론트엔드 연동 및 Swagger](docs/FRONTEND_DEV.md)
- [GitHub Actions 운영](.github/workflows/README.md)

## 프로젝트 성격

본 프로젝트는 KB IT's Your Life 교육 과정의 팀 프로젝트로 제작되었습니다.
별도의 오픈소스 라이선스는 현재 부여하지 않았습니다.
