# 티니머니 백엔드 구조와 개발 규칙

이 문서는 새 도메인을 어디에 만들고, 어떤 파일을 함께 수정해야 하는지 설명합니다.
현재 저장소의 실제 구조를 기준으로 하며, API 구현 규칙의 기준 문서입니다.

## 1. 기술 전제

- Spring Boot가 아닌 Spring Legacy MVC 프로젝트입니다.
- Java 17, Gradle, WAR, Tomcat 9, `javax.servlet`을 사용합니다.
- JPA가 아닌 MyBatis와 직접 작성한 SQL을 사용합니다.
- 공통 응답은 `ApiResponse<T>` 형식입니다.
- API 명세는 코드에서 자동 생성하지 않고 OpenAPI YAML로 직접 관리합니다.

## 2. 현재 구조

```text
teenymoney-backend/
├── build.gradle
├── settings.gradle
├── docs/
│   ├── PROJECT_STRUCTURE.md
│   ├── LOCAL_TEST.md
│   └── FRONTEND_DEV.md
├── sql/
│   ├── schema/
│   ├── migration/
│   ├── seed/
│   └── README.md
└── src/
    ├── main/
    │   ├── java/com/teenyfin/teenymoney/
    │   │   ├── config/
    │   │   │   ├── WebConfig.java
    │   │   │   ├── ServletConfig.java
    │   │   │   ├── RootConfig.java
    │   │   │   ├── RedisConfig.java
    │   │   │   └── SecurityConfig.java
    │   │   ├── domain/
    │   │   ├── global/
    │   │   │   ├── exception/
    │   │   │   │   ├── BusinessException.java
    │   │   │   │   ├── ErrorCode.java
    │   │   │   │   └── GlobalExceptionAdvice.java
    │   │   │   ├── response/
    │   │   │   │   ├── ApiResponse.java
    │   │   │   │   └── PageResponse.java
    │   │   │   └── health/
    │   │   │       ├── controller/
    │   │   │       ├── dto/response/
    │   │   │       ├── service/
    │   │   │       ├── mapper/
    │   │   │       └── vo/
    │   └── resources/
    │       ├── application.properties
    │       ├── mybatis-config.xml
    │       ├── log4j2.xml
    │       ├── log4jdbc.log4j2.properties
    │       ├── openapi/
    │       │   └── teenymoney-api.yaml
    │       ├── swagger-ui/
    │       │   └── swagger-initializer.js
    │       └── com/teenyfin/teenymoney/
    │           ├── mapper/MapperTemplate.xml
    │           └── global/health/mapper/HealthMapper.xml
    └── test/java/com/teenyfin/teenymoney/
        ├── config/InfrastructureConfigTest.java
        └── global/response/ApiResponseFormatTest.java
```

최상위 패키지의 책임은 다음과 같습니다.

| 패키지 | 역할 |
| --- | --- |
| `config` | Spring Root/Servlet Context, MyBatis, Redis, Security 등 애플리케이션 구성 |
| `global` | 여러 도메인이 공유하는 응답, 예외, 인증, 보안, 상태 확인 기능 |
| `domain` | 회원, 지갑, 결제 등 비즈니스 기능별 구현 |

실제 업무 기능은 `domain` 아래에 기능 단위로 만듭니다. 계층 이름을 최상위
패키지로 올리지 않습니다.

## 3. 새 도메인의 표준 구조

예를 들어 지갑 도메인은 다음 전체 경로에 구성합니다.

```text
src/main/java/com/teenyfin/teenymoney/domain/wallet/
├── controller/
│   └── WalletController.java
├── dto/
│   ├── request/
│   │   └── WalletChargeRequestDTO.java
│   └── response/
│       └── WalletBalanceResponseDTO.java
├── service/
│   └── WalletService.java
├── mapper/
│   └── WalletMapper.java
└── vo/
    └── WalletVO.java
```

Mapper XML은 Java 파일 옆이 아니라 resources의 동일한 패키지 경로에 둡니다.

```text
src/main/resources/com/teenyfin/teenymoney/domain/wallet/mapper/WalletMapper.xml
```

계층의 역할과 의존 방향은 다음과 같습니다.

```text
HTTP 요청
  -> Controller
  -> Service
  -> Mapper
  -> Mapper XML / DB
  -> VO
  -> Service에서 Response DTO로 변환
  -> ApiResponse
```

- Controller: HTTP 입력과 출력만 처리합니다.
- Request/Response DTO: 외부 API 계약을 표현합니다.
- Service: 업무 규칙과 트랜잭션 경계를 담당합니다.
- Mapper: DB 작업을 선언합니다.
- VO: Mapper가 조회하거나 저장하는 DB 데이터를 표현합니다.

DB를 사용하지 않는 API는 Mapper와 VO를 만들 필요가 없습니다.

## 4. MyBatis 규칙

Mapper 인터페이스에는 `@Mapper`를 붙입니다.

```java
@Mapper
public interface WalletMapper {
}
```

XML의 `namespace`는 Mapper 인터페이스의 FQCN과 정확히 같아야 합니다.

```xml
<mapper namespace="com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper">
</mapper>
```

XML 파일명은 `*Mapper.xml`로 끝나야 합니다. `RootConfig`가 다음 패턴으로 로딩합니다.

```text
classpath*:com/teenyfin/teenymoney/**/mapper/*Mapper.xml
```

### DDL 변경 관리

현재 Flyway는 도입되지 않았으므로 SQL 파일을 자동으로 적용하지 않습니다.

```text
sql/
├── schema/       # 현재 전체 스키마
├── migration/    # 순서대로 적용할 변경 SQL
└── seed/         # 로컬 전용 테스트 데이터
```

EC2에는 Pull Request 검토가 끝난 SQL만 반영합니다. 이미 적용한 migration
파일은 수정하지 않고 새 파일을 추가하며, 상세 규칙은 `sql/README.md`를 따릅니다.

## 5. API 구현 시 OpenAPI 갱신은 필수

> Controller에 외부 API를 추가하거나 API 계약을 변경했다면
> `src/main/resources/openapi/teenymoney-api.yaml`도 같은 작업에서 반드시 수정합니다.
> YAML이 갱신되지 않은 API는 구현이 완료된 것으로 보지 않습니다.

다음 변경은 모두 OpenAPI 갱신 대상입니다.

- API 경로 또는 HTTP 메서드 추가·변경·삭제
- Path, Query, Header 파라미터 변경
- Request DTO와 Response DTO의 필드 또는 타입 변경
- 성공 및 실패 HTTP 상태 변경
- 공통 또는 도메인 ErrorCode 추가
- JWT 인증과 부모·자녀 권한 조건 변경
- 프론트엔드가 참고할 요청·응답 예시 변경

도메인 API 하나의 완료 조건은 다음과 같습니다.

```text
[ ] Controller 구현
[ ] Request/Response DTO 구현
[ ] Service 구현
[ ] 필요한 경우 Mapper, VO, Mapper XML 구현
[ ] 검증 또는 테스트 완료
[ ] teenymoney-api.yaml의 tags, paths, schemas, examples 갱신
[ ] Swagger UI에서 명세 확인
[ ] Postman 또는 실제 클라이언트로 호출 확인
```

현재 명세는 수동 YAML 방식입니다. Java Controller나 DTO를 바꿔도 Swagger 문서는
자동으로 바뀌지 않습니다. 코드와 YAML은 같은 브랜치와 커밋 또는 PR에서 관리합니다.

Swagger 관련 경로는 다음과 같습니다.

```text
Swagger UI  : /swagger-ui/index.html
OpenAPI YAML: /api-docs/teenymoney-api.yaml
```

Swagger UI의 `index.html`, CSS, JavaScript는 WebJar가 제공합니다. 저장소에는 커스텀
초기화 파일과 OpenAPI YAML만 둡니다.

## 6. Spring 컨텍스트

```text
Tomcat
  -> WebConfig
       ├── RootConfig + RedisConfig + SecurityConfig
       │     DataSource, MyBatis, TransactionManager, Redis, Security
       └── ServletConfig
             DispatcherServlet, Controller 스캔, 정적 리소스
```

자식 서블릿 컨텍스트는 부모 루트 컨텍스트의 빈을 볼 수 있습니다. 반대 방향은
불가능합니다. Controller가 Service와 Mapper를 사용할 수 있는 이유가 이 구조입니다.

## 7. 공통 응답과 예외

모든 JSON API는 `ApiResponse<T>`를 사용합니다.

```json
{
  "success": true,
  "code": "OK",
  "message": "성공",
  "data": {}
}
```

예상 가능한 업무 실패는 Service에서 `BusinessException`으로 던집니다.
`GlobalExceptionAdvice`가 `ErrorCode`에 정의된 HTTP 상태와 공통 응답으로 변환합니다.
Controller에서 공통 예외를 임의로 잡아 다른 응답 형태를 만들지 않습니다.

## 8. 환경변수와 빌드

DB 설정은 실행 환경에서 주입해야 합니다.

```text
DB_DRIVER
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_HOST
REDIS_PORT
```

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`가 없으면 애플리케이션이 정상 기동하지 않습니다.
실제 DB 연결은 `/api/v1/health/db`에서 확인합니다.

```bash
./gradlew test
./gradlew war
```

배포 파일은 `build/libs/ROOT.war`입니다.

## 9. 배포 확인 사항

- Swagger UI와 OpenAPI 외부 접근을 위해 Nginx에서 `/swagger-ui/`와 `/api-docs/`를
  Tomcat으로 전달해야 합니다.
