# 로컬 빌드와 API 확인

이 문서는 코드를 배포하기 전에 로컬에서 빌드, Health API, Swagger UI를 확인하는
절차를 설명합니다.

## 1. 준비

- JDK 17
- Tomcat 9
- Health DB 확인이 필요하면 MySQL 8

DB 설정은 파일에 비밀번호를 기록하지 않고 환경변수로 주입합니다.

```powershell
$env:DB_DRIVER = 'net.sf.log4jdbc.sql.jdbcapi.DriverSpy'
$env:DB_URL = 'jdbc:log4jdbc:mysql://localhost:3306/teenymoney?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Seoul'
$env:DB_USERNAME = '<local-user>'
$env:DB_PASSWORD = '<local-password>'
```

DriverSpy를 사용하면 URL도 `jdbc:log4jdbc:mysql://...` 형식이어야 합니다. 일반 MySQL
URL을 사용하려면 드라이버도 함께 바꿉니다.

```powershell
$env:DB_DRIVER = 'com.mysql.cj.jdbc.Driver'
$env:DB_URL = 'jdbc:mysql://localhost:3306/teenymoney?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Seoul'
```

Redis 기본값은 `localhost:6379`이고 비밀번호는 비어 있습니다. 다른 Redis를
사용하면 `REDIS_HOST`, `REDIS_PORT`를 설정하고, `requirepass`가 걸린 서버라면
`REDIS_PASSWORD`도 넣습니다.

```powershell
# SSH 터널로 EC2 Redis를 쓰는 경우 예시
$env:REDIS_PORT = '16379'
$env:REDIS_PASSWORD = '<requirepass 값>'
```

`REDIS_PASSWORD`가 틀려도 앱은 정상 기동합니다. Lettuce가 연결을 지연 생성하므로
실패는 첫 Redis 명령에서 `NOAUTH`로 나타납니다.

`COOKIE_SECURE`는 필수 환경변수입니다. 로컬 HTTP에서는 `false`로 설정합니다.
`true`로 켜면 브라우저가 Refresh 쿠키를 저장하지 않아 로그인은 되는데 재발급만
실패합니다.

환경변수 전체 목록과 EC2 설정은 [배포 문서](DEPLOY.md)를 참고합니다.

`JWT_SECRET`도 로컬과 운영 모두 필수입니다. 로컬에서는 운영과 다른 Base64 키를
생성하여 실행 환경에 주입합니다(자세한 내용은 [README 환경변수](../README.md#환경변수) 참고).

```powershell
# 로컬용 키를 한 번 생성한 뒤 IntelliJ/Tomcat 실행 환경에 저장한다.
$env:JWT_SECRET = '<openssl rand -base64 32 결과>'
$env:COOKIE_SECURE = 'false'
```

### IntelliJ에서 실제 DB를 사용하는 MemberMapper 테스트

`MemberMapperTest`는 Tomcat 없이 JUnit으로 실행하며, EC2 MySQL에 연결할 때는 먼저
별도 PowerShell 창에서 SSH 터널을 열어 둡니다.

```powershell
ssh -N teenymoney
Test-NetConnection 127.0.0.1 -Port 13306
```

`TcpTestSucceeded`가 `True`이면 IntelliJ에서 다음 순서로 실행 구성을 만듭니다.

1. `MemberMapperTest.java`를 열고 클래스 왼쪽의 실행 아이콘을 누릅니다.
2. **Modify Run Configuration...** 또는 **Run > Edit Configurations...**를 엽니다.
3. JUnit 실행 구성의 **Environment variables**에 아래 값을 입력합니다.

```text
DB_URL=jdbc:log4jdbc:mysql://127.0.0.1:13306/teenymoney?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false
DB_USERNAME=<EC2 MySQL 사용자>
DB_PASSWORD=<EC2 MySQL 비밀번호>
```

4. `MemberMapperTest` 클래스 전체를 다시 실행합니다.

정상이라면 테스트 3개가 모두 통과합니다. `insertAppliesRoleSpecificTeenyScorePolicy`
테스트가 부모의 `teeny_score`는 `NULL`, 자녀는 DB 기본값 `600`인지 실제 DB에서
확인합니다. 각 테스트는 `@Transactional`로 롤백되므로 생성한 회원 행은 남지 않습니다.

테스트가 실패하지 않고 **Skipped**로 표시되면 위 환경변수 중 하나가 해당 JUnit 실행
구성에 빠진 것입니다. 이 테스트에는 Redis, JWT, Cookie 환경변수가 필요하지 않습니다.
IntelliJ 실행 구성의 비밀번호는 로컬 `.idea/workspace.xml`에만 두고 공유하거나 저장소에
커밋하지 않습니다.

## 2. 테스트와 WAR 빌드

```powershell
.\gradlew.bat test
.\gradlew.bat war
```

`ApiResponseFormatTest`는 DB와 Tomcat 없이 공통 응답 및 예외 계약을 확인합니다.

`InfrastructureConfigTest`는 `RedisConfig`와 `SecurityConfig`만 로딩하고 필요한
값(`redis.*`, `jwt.*`)을 `@TestPropertySource`로 직접 넣습니다. 실제 DB를 사용하는
`MemberMapperTest`는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`가 없으면 건너뛰므로
**전체 테스트 명령 자체는 환경변수 없이도 통과합니다.**

```powershell
.\gradlew.bat clean test     # DB_URL 등을 설정하지 않아도 통과한다
```

환경변수 없이 실행할 때는 실제 DB나 Redis에 접속하지 않고 빈 등록과 컨텍스트 구성을
확인합니다. 실제 DB 연결은 위 `MemberMapperTest` 또는 Tomcat의 `/api/v1/health/db`로
확인합니다.

빌드 결과:

```text
build/libs/ROOT.war
```

## 3. Tomcat 배포

Tomcat 경로는 각자의 설치 위치에 맞춥니다.

```powershell
$tc = '<tomcat-directory>'

Remove-Item "$tc\webapps\ROOT" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item "$tc\webapps\ROOT.war" -Force -ErrorAction SilentlyContinue
Copy-Item build\libs\ROOT.war "$tc\webapps\ROOT.war"
```

환경변수를 설정한 셸에서 Tomcat을 시작해야 합니다.

```powershell
$env:CATALINA_HOME = $tc
& "$env:CATALINA_HOME\bin\catalina.bat" run
```

## 4. Health API 확인

```powershell
curl.exe -i http://localhost:8080/api/v1/health
curl.exe -i http://localhost:8080/api/v1/health/db
```

애플리케이션 정상 응답:

```json
{
  "success": true,
  "code": "OK",
  "message": "성공",
  "data": {
    "status": "UP",
    "time": "2026-07-28 12:00:00"
  }
}
```

DB 정상 응답:

```json
{
  "success": true,
  "code": "OK",
  "message": "성공",
  "data": {
    "database": "teenymoney"
  }
}
```

DB 연결 또는 쿼리 실패:

```json
{
  "success": false,
  "code": "COMMON_SERVICE_UNAVAILABLE",
  "message": "서비스를 일시적으로 사용할 수 없습니다.",
  "data": null
}
```

DB 실패 응답의 HTTP 상태는 503입니다. `/health`가 200이고 `/health/db`가 503이면
애플리케이션은 기동했지만 DB에 접근하지 못한 상태입니다.

## 5. Swagger와 OpenAPI 확인

브라우저:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI 원본:

```text
http://localhost:8080/api-docs/teenymoney-api.yaml
```

확인 항목:

```text
[ ] Swagger UI가 빈 화면이나 Petstore가 아닌 TeenyMoney API를 표시한다.
[ ] Health API 두 개가 표시된다.
[ ] Try it out에서 현재 서버의 API가 호출된다.
[ ] /api-docs/teenymoney-api.yaml이 HTTP 200으로 열린다.
```

Swagger는 정적 YAML을 읽습니다. Controller와 DTO를 변경했다면 테스트 전에
`src/main/resources/openapi/teenymoney-api.yaml`을 반드시 함께 수정합니다.

## 6. 배포 전 확인

```text
[ ] 테스트 통과
[ ] ROOT.war 생성
[ ] /api/v1/health HTTP 200
[ ] /api/v1/health/db HTTP 200 또는 의도한 503
[ ] /swagger-ui/index.html HTTP 200
[ ] /api-docs/teenymoney-api.yaml HTTP 200
[ ] 실제 응답과 OpenAPI 스키마 및 예시 일치
```
