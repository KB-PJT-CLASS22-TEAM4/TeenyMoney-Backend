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

## 2. 테스트와 WAR 빌드

```powershell
.\gradlew.bat test
.\gradlew.bat war
```

`ApiResponseFormatTest`는 DB와 Tomcat 없이 공통 응답 및 예외 계약을 확인합니다.

`InfrastructureConfigTest`는 `RedisConfig`와 `SecurityConfig`만 로딩하고 필요한
값(`redis.*`, `jwt.*`)을 `@TestPropertySource`로 직접 넣습니다. `RootConfig`를
로딩하지 않으므로 **전체 테스트는 환경변수 없이 실행됩니다.**

```powershell
.\gradlew.bat clean test     # DB_URL 등을 설정하지 않아도 통과한다
```

실제 DB나 Redis에 접속하지 않고 빈 등록과 컨텍스트 구성만 확인합니다. 실제 연결
확인은 Tomcat으로 띄운 뒤 `/api/v1/health/db`에서 합니다.

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
