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
