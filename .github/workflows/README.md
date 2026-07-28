# GitHub Actions

이 폴더는 TeenyMoney Backend의 자동 검증 workflow를 관리합니다.

## 실행 시점

CI는 다음 경우에 실행됩니다.

- `dev`, `main` 브랜치를 대상으로 하는 Pull Request
- `dev`, `main` 브랜치 push
- GitHub Actions 화면에서 수동 실행

같은 브랜치에서 새 실행이 시작되면 이전 실행은 취소됩니다.

## CI 구성

### repo-policy

저장소에 필요한 백엔드 파일과 보안 규칙을 검사합니다.

- Gradle Wrapper와 빌드 설정 파일 존재 확인
- `application.properties` 존재 확인
- OpenAPI 명세와 Swagger 초기화 파일 존재 확인
- SQL 변경 관리 문서 존재 확인
- Pull Request 및 Issue 템플릿 존재 확인
- `.idea`, 실제 `.env`, 로컬 설정, 키 및 인증서 파일 커밋 방지
- DB URL, 사용자명, 비밀번호가 환경변수를 사용하도록 강제

### backend-build

GitHub-hosted Ubuntu runner에서 다음 검증을 수행합니다.

- Temurin Java 17 설정
- Gradle Wrapper 무결성 검증
- Gradle 의존성 캐시 사용
- 전체 테스트 실행
- `ROOT.war` 빌드
- WAR 파일 생성 여부 확인

테스트는 실제 MySQL이나 Redis 서버에 연결하지 않습니다. 운영 및 개발 DB 자격증명을
CI에 주입하지 않습니다.

테스트 실패 시 테스트 보고서를 7일 동안 artifact로 보관합니다. `dev` 또는 `main`
브랜치에 push된 빌드는 `ROOT.war`를 7일 동안 artifact로 보관합니다.

## Required Check

Repository ruleset 또는 branch protection에서 다음 check를 필수로 지정합니다.

- `repo-policy`
- `backend-build`

두 check가 모두 성공하기 전에는 `dev`, `main` 브랜치로 병합하지 않습니다.

## 배포 분리

이 workflow는 검증과 빌드만 담당하며 EC2 배포는 수행하지 않습니다. 배포 자동화를
추가할 때는 별도 workflow와 GitHub Environment를 사용하고, 배포 후
`/api/v1/health`와 `/api/v1/health/db`를 smoke test로 확인합니다.
