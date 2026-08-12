# EC2 배포와 환경 설정

WAR 하나를 로컬과 EC2 양쪽에서 돌립니다. **빌드 산출물은 동일하고 환경변수만
다릅니다.** 환경별 properties 파일을 나누지 않는 이유가 이것입니다 — 파일이
갈라지면 "로컬에서는 됐는데 EC2에서 안 되는" 차이가 코드에 숨습니다.

## 1. 환경변수 대조표

애플리케이션이 읽는 값 전부입니다. 기준은 `src/main/resources/application.properties`.

| 변수 | 기본값 | 로컬 | EC2 | 없으면 |
| --- | --- | --- | --- | --- |
| `DB_DRIVER` | DriverSpy | 그대로 | 그대로 | 기본값 사용 |
| `DB_URL` | **없음** | 아래 참고 | `127.0.0.1:3306` | **기동 실패** |
| `DB_USERNAME` | **없음** | 로컬/EC2 계정 | EC2 계정 | **기동 실패** |
| `DB_PASSWORD` | **없음** | 〃 | 〃 | **기동 실패** |
| `REDIS_HOST` | `localhost` | `localhost` | `127.0.0.1` | 기본값 사용 |
| `REDIS_PORT` | `6379` | **`16379`** (터널) | `6379` | 기본값 사용 |
| `REDIS_PASSWORD` | 빈 값 | EC2 Redis 값 | EC2 Redis 값 | 첫 명령에서 `NOAUTH` |
| `JWT_SECRET` | **없음** | 로컬 전용 키 | 운영 고정 키 | **기동 실패** |
| `JWT_ACCESS_EXPIRATION_MS` | 1800000 | 그대로 | 그대로 | 기본값 사용 |
| `JWT_REFRESH_EXPIRATION_MS` | 1209600000 | 그대로 | 그대로 | 기본값 사용 |
| `COOKIE_SECURE` | **없음** | `false` | **`true`** | **기동 실패** |
| `AWS_REGION` | `ap-northeast-2` | 그대로 | 그대로 | 기본값 사용 |
| `AWS_S3_BUCKET` | **없음** | `teenymoney-media-kb22` | 〃 | **기동 실패** |
| `AWS_S3_PRESIGN_TTL_SECONDS` | 600 | 그대로 | 그대로 | 기본값 사용 |
| `AWS_ACCESS_KEY_ID` | **없음** | IAM 사용자 키 | **설정하지 않음** | 첫 업로드에서 실패 |
| `AWS_SECRET_ACCESS_KEY` | **없음** | 〃 | **설정하지 않음** | 〃 |

**실질적으로 다른 것은 다섯 개뿐입니다**: DB/Redis 접속 지점, `JWT_SECRET`,
`COOKIE_SECURE`, 그리고 AWS 자격증명. 나머지는 양쪽 동일합니다.

### AWS 자격증명

`AWS_ACCESS_KEY_ID`와 `AWS_SECRET_ACCESS_KEY`는 **로컬에만** 넣습니다. EC2는 인스턴스
IAM 역할(`teenymoney-ec2-s3`)로 자격증명을 받으므로 서버에 키를 두지 않습니다.
`DefaultCredentialsProvider`가 환경변수 → 프로파일 → 인스턴스 메타데이터 순으로 찾기
때문에 코드는 양쪽이 같습니다.

이 두 이름은 AWS SDK가 정한 것이라 바꿀 수 없고, `application.properties`에 적지도
않습니다. SDK가 OS 환경변수에서 직접 읽습니다. 반면 `AWS_REGION`과 `AWS_S3_BUCKET`은
우리 코드가 읽는 값이라 `application.properties`에 자리표시자가 있습니다.

자격증명 탐색은 지연 실행이라 값이 없어도 앱은 정상 기동하고 **첫 업로드에서야**
실패합니다. `JWT_SECRET`처럼 기동 시점에 드러나지 않으므로, 배포 후 프로필 이미지
업로드를 한 번 실제로 해봐야 확인됩니다.

버킷은 비공개입니다. 퍼블릭 액세스 차단을 풀지 마세요 — 조회 URL은 요청마다 서명해서
발급하며, 버킷을 공개로 바꾸면 미성년자 사진이 URL만으로 영구 노출됩니다.

### 퀘스트 인증 이미지 Lifecycle — 인프라 설정, 코드 아님

퀘스트 인증 사진은 `quest-verifications/{questId}/{uuid}.{확장자}` 키로 올라갑니다.
**업로드 90일 뒤 자동 삭제**가 정책이고, 애플리케이션은 이 삭제에 관여하지 않습니다.
서버에는 `s3:DeleteObject` 권한이 없으며 줄 계획도 없습니다. 버킷의 Lifecycle 규칙만이
지웁니다. **버킷당 한 번** 아래 규칙을 넣어야 하고, 넣지 않으면 사진이 영구 보관됩니다.

```json
{
  "Rules": [
    {
      "ID": "quest-verification-image-90d",
      "Status": "Enabled",
      "Filter": { "Prefix": "quest-verifications/" },
      "Expiration": { "Days": 90 }
    }
  ]
}
```

```bash
aws s3api put-bucket-lifecycle-configuration --bucket "$AWS_S3_BUCKET" --lifecycle-configuration file://quest-lifecycle.json
```

적용 확인:

```bash
aws s3api get-bucket-lifecycle-configuration --bucket "$AWS_S3_BUCKET"
```

접두사를 `quest-verifications/`로 정확히 맞추세요. 회원 프로필 이미지는 `profile/`
접두사를 쓰며 삭제 대상이 **아닙니다**. 규칙을 버킷 전체에 걸면 프로필 사진이 90일 뒤
사라집니다.

이 규칙은 두 가지를 함께 처리합니다.

1. **보관 기간이 끝난 인증 사진 삭제.** 삭제 뒤에도 DB의 인증 글, 상태, 시각, 반려 사유는
   남고, 조회 API는 서명 URL 대신 `imageExpired=true`를 반환합니다. 만료 판정은 S3가
   아니라 인증 행의 `created_at` 기준이라 실제 삭제와 화면 표시가 어긋나지 않습니다.
2. **고아 객체 정리.** 인증 제출은 S3 업로드가 DB 트랜잭션보다 먼저라, 업로드 성공 후
   트랜잭션이 롤백되면 아무도 참조하지 않는 오브젝트가 남습니다. DB에 키가 저장되지
   않았으므로 어떤 화면에도 나타나지 않고, 90일 뒤 이 규칙이 지웁니다.

| 항목 | 담당 |
| --- | --- |
| 규칙 적용 | 인프라 담당자가 운영 버킷(`teenymoney-media-kb22`)에 1회 |
| 로컬 개발 | 규칙 없이도 동작합니다. 로컬 테스트 객체는 수동으로 정리하세요 |
| 코드 변경 시 | 접두사를 바꾸면 이 규칙도 함께 바꿔야 합니다 |

### 필수 보안값

`JWT_SECRET`과 `COOKIE_SECURE`가 없으면 애플리케이션은 기동하지 않습니다.
`JWT_SECRET` 값은 반드시 Base64여야 하며, 운영 키는 재배포할 때 새로 만들지 않고
안전한 비밀 저장소에 보관한 같은 값을 계속 사용합니다.

```bash
openssl rand -base64 32
```

Base64가 아닌 문자열(예: `my-secret-key`)을 넣으면 `Base64.getDecoder().decode()`가
`IllegalArgumentException`을 던져 컨텍스트 로딩이 통째로 깨집니다. 원인이 로그에
잘 드러나지 않으므로 주의합니다.

`COOKIE_SECURE`는 HTTPS에서 `true`여야 하고, 로컬 http에서는 반드시 `false`여야
합니다. 로컬에서 `true`로 켜면 브라우저가 Refresh 쿠키를 **저장조차 하지 않아**
로그인은 되는데 재발급만 실패합니다.

## 2. 로컬 설정

DB를 어디에 붙일지에 따라 둘 중 하나를 고릅니다.

### (A) 로컬 MySQL — 기본 방식

README의 데이터베이스 개발 원칙에 맞는 방식입니다. 개인 기능 개발과 SQL 실험은
여기서 합니다.

```powershell
$env:DB_URL = 'jdbc:log4jdbc:mysql://localhost:3306/teenymoney?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME = '<로컬 계정>'
$env:DB_PASSWORD = '<로컬 비밀번호>'
# Redis 로컬이면 REDIS_* 는 설정하지 않아도 된다 (기본값 localhost:6379, 비밀번호 없음)
```

### (B) SSH 터널로 EC2 MySQL·Redis

통합 데이터를 그대로 보며 작업할 때 씁니다. `~/.ssh/config`:

```sshconfig
Host teenymoney-ec2
    HostName <EC2 주소>
    User <계정>
    IdentityFile ~/.ssh/<키>
    LocalForward 13306 127.0.0.1:3306
    LocalForward 16379 127.0.0.1:6379
```

```powershell
$env:DB_URL = 'jdbc:log4jdbc:mysql://localhost:13306/teenymoney?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME = '<EC2 계정>'
$env:DB_PASSWORD = '<EC2 비밀번호>'
$env:REDIS_PORT = '16379'
$env:REDIS_PASSWORD = '<EC2 Redis requirepass>'
```

터널이 끊기면 앱은 그대로 뜬 채 DB만 실패합니다(`initializationFailTimeout=-1`).
`/api/v1/health`는 200인데 `/api/v1/health/db`가 503이면 터널부터 확인합니다.

> **주의:** EC2는 팀 공용 통합 환경입니다. `INSERT`/`UPDATE`를 수행하는 테스트나
> seed 재적용을 이 연결로 실행하면 다른 팀원의 작업 데이터가 바뀝니다.
> 조회는 (B), 쓰기는 (A)로 나누는 것을 권장합니다.

### 값을 어디에 넣는가

| 방법 | 적용 범위 | 비고 |
| --- | --- | --- |
| IntelliJ 실행 구성 → Environment variables | 그 실행 구성만 | **팀 표준.** 프로젝트별로 분리됨 |
| Windows 사용자 환경변수 | 그 계정의 새 프로세스 전부 | `HKCU\Environment`에 평문 저장. **IDE 재시작 필요** |
| PowerShell `$env:` | 그 셸에서 띄운 프로세스만 | 창을 닫으면 사라짐 |

셋을 섞으면 어느 값이 적용됐는지 추적하기 어렵습니다. IntelliJ 실행 구성 하나로
모으는 편이 낫습니다(실행 구성 값이 상속받은 환경을 덮어씁니다).

`.env` 파일은 이 프로젝트에서 동작하지 않습니다. Spring Boot가 아니라 순수 Spring
MVC이고 `.env`를 읽는 코드가 없습니다.

## 3. EC2 설정

### 값을 넣는 위치

Tomcat은 셸 환경을 물려받지 않습니다. 서비스로 기동되기 때문입니다. 두 곳 중
**실제로 쓰이는 쪽**에 넣어야 합니다.

```bash
systemctl cat tomcat9 2>/dev/null || systemctl cat tomcat
```

- 출력에 `EnvironmentFile=`이 있으면 → 그 파일(보통 `/etc/default/tomcat9`)
- 없으면(직접 설치한 tarball) → `$CATALINA_HOME/bin/setenv.sh`

`setenv.sh`는 `catalina.sh`가 기동할 때 자동으로 읽습니다. 없으면 새로 만들면
됩니다.

```bash
sudo tee "$CATALINA_HOME/bin/setenv.sh" > /dev/null <<'EOF'
#!/bin/sh
export DB_DRIVER='net.sf.log4jdbc.sql.jdbcapi.DriverSpy'
export DB_URL='jdbc:log4jdbc:mysql://127.0.0.1:3306/teenymoney?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false'
export DB_USERNAME='<계정>'
export DB_PASSWORD='<비밀번호>'

export REDIS_HOST='127.0.0.1'
export REDIS_PORT='6379'
export REDIS_PASSWORD='<requirepass 값>'

export JWT_SECRET='<openssl rand -base64 32 결과>'
export COOKIE_SECURE='true'
EOF

sudo chown <tomcat실행계정>:<그룹> "$CATALINA_HOME/bin/setenv.sh"
sudo chmod 600 "$CATALINA_HOME/bin/setenv.sh"
```

**`chmod 600`이 중요합니다.** 이 파일이 EC2에서 유일한 비밀 보관처가 되므로,
서버에 접속 가능한 다른 계정이 읽을 수 있으면 안 됩니다.

이 파일은 서버에만 존재하고 저장소에 커밋하지 않습니다. **WAR를 교체해도
`setenv.sh`는 그대로 남으므로 재배포마다 다시 설정할 필요가 없습니다.**

### Docker 컨테이너 접속 지점

MySQL과 Redis가 Docker로 떠 있고 포트를 호스트에 publish한 구성이므로,
Tomcat 입장에서는 `127.0.0.1`입니다. 확인:

```bash
docker ps --format '{{.Names}}\t{{.Ports}}'
```

`0.0.0.0:3306->3306/tcp`로 보이면 외부에도 열려 있다는 뜻입니다. 보안 그룹에서
막혀 있는지 확인하고, 가능하면 `127.0.0.1:3306->3306/tcp`로 바꿉니다.

### 인스턴스가 여러 대라면

`JWT_SECRET`이 **모든 인스턴스에서 같아야 합니다.** A 서버가 발급한 토큰을 B 서버가
검증하기 때문입니다. 또 `exp` 검증에 허용 오차가 없으므로 서버 시각(NTP)이
동기화되어 있어야 합니다.

## 4. 배포 절차

```bash
# 로컬
./gradlew clean test war
scp build/libs/ROOT.war <계정>@<EC2>:/tmp/ROOT.war
```

```bash
# EC2
sudo systemctl stop tomcat9

# 이전 배포의 압축 해제 디렉터리를 반드시 지운다.
# 남아 있으면 Tomcat이 새 WAR를 풀지 않고 옛 클래스를 그대로 쓴다.
sudo rm -rf "$CATALINA_HOME/webapps/ROOT" "$CATALINA_HOME/webapps/ROOT.war"

sudo cp /tmp/ROOT.war "$CATALINA_HOME/webapps/ROOT.war"
sudo chown <tomcat실행계정>:<그룹> "$CATALINA_HOME/webapps/ROOT.war"
sudo systemctl start tomcat9
```

파일명이 `ROOT.war`여야 컨텍스트 경로가 `/`가 되어 API가 `/api/v1/...`로 붙습니다.
`build.gradle`이 이름을 고정하고 있으므로 그대로 복사합니다.

## 5. 배포 후 확인

```bash
curl -i https://www.teenymoney.kro.kr/api/v1/health
curl -i https://www.teenymoney.kro.kr/api/v1/health/db
```

```text
[ ] /api/v1/health          200, data.status = "UP"
[ ] /api/v1/health/db       200, data.database = 의도한 DB 이름
[ ] /swagger-ui.html        200
[ ] /v2/api-docs            200
```

`/health`가 200인데 `/health/db`가 503이면 **애플리케이션은 떴고 DB만 실패한**
상태입니다. `DB_URL`, 자격증명, 컨테이너 기동 여부를 확인합니다.

## 6. 자주 터지는 것

| 증상 | 원인 |
| --- | --- |
| 기동 직후 `IllegalArgumentException` | `JWT_SECRET`이 Base64가 아님 |
| 앱은 뜨는데 `/health/db` 503 | `DB_URL`·자격증명 오류, 컨테이너 정지, 터널 끊김 |
| 첫 접속부터 `Public Key Retrieval is not allowed` | `DB_URL`에 `allowPublicKeyRetrieval=true` 누락 (`caching_sha2_password`) |
| 로그인은 되는데 재발급만 실패 | `COOKIE_SECURE`가 환경과 반대 (http에 `true`) |
| Redis 명령에서 `NOAUTH` | `REDIS_PASSWORD` 미설정 또는 불일치. 앱 기동 시에는 드러나지 않음 |
| 코드를 바꿨는데 반영 안 됨 | `webapps/ROOT` 디렉터리를 지우지 않고 WAR만 교체 |
| 환경변수를 바꿨는데 반영 안 됨 | 프로세스 재시작 필요. 이미 뜬 프로세스는 옛 환경을 유지 |
| 404가 나야 할 경로에서 401 | 정상. 인가 판단이 먼저 끝난다 (README "인가 규칙" 참고) |

## 7. 아직 자동화되지 않은 것

CI(`.github/workflows/ci.yml`)는 검증과 WAR 빌드까지만 하고 배포하지 않습니다.
현재 배포는 위 절차를 수동으로 수행합니다. 자동화를 추가할 때는 별도 workflow와
GitHub Environment를 쓰고, 배포 후 `/api/v1/health`와 `/api/v1/health/db`를
smoke test로 확인합니다.
