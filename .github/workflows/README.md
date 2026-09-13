# GitHub Actions

TeenyMoney Backend의 검증과 개발 서버 배포 workflow를 관리합니다.

| 파일 | 이름 | 실행 시점 |
| --- | --- | --- |
| `ci.yml` | CI | `dev`, `main` 대상 Pull Request, `main` push, 수동 실행 |
| `deploy-dev.yml` | Deploy development | `dev` push, `dev` 브랜치 수동 실행 |

## CI (`ci.yml`)

같은 브랜치에서 새 실행이 시작되면 이전 실행은 취소됩니다.

### repo-policy

- Gradle Wrapper, `build.gradle`, `settings.gradle`, `application.properties` 존재 확인
- `sql/README.md`, Pull Request 템플릿, Issue 템플릿 폴더 존재 확인
- `.idea`, 실제 `.env`, `application-local.properties`, 키·인증서 파일 커밋 차단
- `application.properties`의 DB URL, 사용자명, 비밀번호가 환경변수(`${DB_URL}` 등)를 쓰는지 확인

### backend-build

- Temurin Java 17과 Gradle 설정, Wrapper 검증
- `./gradlew clean build`로 전체 테스트와 `ROOT.war` 빌드
- 테스트가 실패하면 테스트 보고서를 7일 동안 artifact로 보관
- `main` push 빌드는 `ROOT.war`를 7일 동안 artifact로 보관

CI는 실제 MySQL, Redis, EC2에 연결하지 않으며 DB 자격증명을 주입하지 않습니다.

### Required Check

Repository ruleset 또는 branch protection에서 `repo-policy`, `backend-build`를 필수 check로 지정합니다.
두 check가 모두 성공하기 전에는 `dev`, `main`에 병합하지 않습니다.

## 개발 서버 배포 (`deploy-dev.yml`)

`dev`에 병합되면 테스트와 WAR 빌드를 다시 실행한 뒤 `development` Environment로 배포합니다.

1. `./gradlew clean test war`로 빌드하고 WAR의 SHA-256 값을 기록합니다.
2. GitHub OIDC로 `AWS_DEPLOY_ROLE_ARN` 역할을 받아 러너 IP를 보안 그룹에 임시로 허용합니다.
3. WAR를 EC2로 전송하고 파일명의 Git SHA와 SHA-256 값을 다시 검증합니다.
4. 기존 WAR를 `/var/backups/teenymoney`에 백업하고 Tomcat을 재시작합니다.
5. `/api/v1/health`, `/api/v1/health/db`, 공개 URL 헬스 체크가 모두 통과해야 성공으로 봅니다.
6. 실패하면 직전 WAR로 자동 복원합니다. 백업은 최근 5개만 남깁니다.
7. 결과와 관계없이 임시 SSH 키, 전송 파일, 보안 그룹 규칙을 정리합니다.

### 필요한 설정

`development` Environment에 다음 값을 등록합니다.

| 종류 | 이름 | 용도 |
| --- | --- | --- |
| Variables | `EC2_HOST`, `EC2_PORT`, `EC2_USER` | SSH 접속 대상 |
| Variables | `EC2_SECURITY_GROUP_ID` | 러너 IP를 임시로 허용할 보안 그룹 |
| Variables | `AWS_DEPLOY_ROLE_ARN` | OIDC로 맡을 배포용 IAM 역할 |
| Secrets | `DEPLOY_SSH_PRIVATE_KEY` | 배포용 SSH 개인 키 |
| Secrets | `EC2_KNOWN_HOSTS` | 호스트 키 검증용 known_hosts |

서버 쪽 준비와 환경변수는 [EC2 배포와 환경 설정](../../docs/DEPLOY.md)을 참고합니다.
