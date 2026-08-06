# Family Link Consume Hardening Design

## 목표

가족 연동 코드 소비 흐름의 Redis 시도 횟수 제한을 원자적으로 만들고, DB 저장 실패 시 동작을 명시적인 fail-closed 정책으로 고정한다. 현재 이슈 범위를 벗어나는 연결 해제·재연결은 후속 작업에서 안전하게 구현할 수 있도록 제약 조건과 구현 규칙을 기록한다.

## 결정

### 코드 소비와 DB 실패

연동 코드는 Redis `GETDEL`이 성공한 순간 소비된 것으로 간주한다. 이후 역할·상태 가드 실패, 중복 관계, DB 예외 또는 트랜잭션 커밋 실패가 발생해도 코드를 Redis에 복원하지 않는다. 호출자는 실패 응답을 받은 뒤 부모에게 새 코드를 요청해야 한다.

복원 로직을 넣지 않는 이유는 단순 `SET` 또는 `SET NX`가 부모의 동시 재발급과 경쟁하여 한 부모에게 두 코드가 살아남게 만들 수 있기 때문이다. Redis와 MySQL을 하나의 트랜잭션으로 묶는 복구 프로토콜은 이번 이슈 범위에서 도입하지 않는다.

### 시도 횟수 제한

`family-link:attempts:{childId}`의 증가와 최초 TTL 설정은 기존 `FamilyLinkCodeStore`의 Redis Lua 실행 패턴을 재사용해 한 번에 처리한다.

```lua
local attempts = redis.call('INCR', KEYS[1])
if attempts == 1 then
    redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
return attempts
```

TTL은 첫 시도에만 설정하며 이후 요청에서 연장하지 않는다. 성공 후 attempts 키는 별도로 지우지 않고 10분 TTL로 만료시킨다.

### DB 트랜잭션

현재 서비스는 사전 조회 한 번과 단일 `INSERT ... SELECT`만 수행하며 Redis는 DB 트랜잭션에 참여하지 않는다. 따라서 `linkChild`의 `@Transactional`을 제거해 Redis 호출 동안 DB 트랜잭션과 커넥션을 유지하지 않도록 한다. 동시 연결의 최종 방어는 DB UNIQUE 제약이 담당한다.

### DB 제약과 재연결

`UQ_MBR_CONN_R_ACTIVE_CHILD(active_child_id)`는 한 자녀가 동시에 하나의 ACTIVE 관계만 갖게 하므로 유지한다. `V005__add_unique_active_child_connection.sql`과 기준 스키마 변경을 같은 커밋에 포함한다.

연결 해제 후 같은 부모와 재연결할 때 포괄적인 `ON DUPLICATE KEY UPDATE`는 사용하지 않는다. 후속 해제 기능은 먼저 정확한 부모·자녀의 INACTIVE 행만 ACTIVE로 변경하고, 변경된 행이 0개일 때만 신규 INSERT한다.

```text
UPDATE exact INACTIVE parent-child pair
  -> 1 row: 재연결 완료
  -> 0 rows: guarded INSERT 수행
```

### API 문서와 코드 정리

`POST /api/v1/families/connect-link`에 200, 400, 401, 403, 409, 429, 503 응답을 Swagger로 명시한다. Mapper의 의미 없는 주석은 삭제하고, `insertConnection`의 0 반환 조건만 역할·상태 가드로 설명한다. 프론트 연동 문서에는 GETDEL 이후 DB 실패 시 코드가 복원되지 않는 정책과 새 코드 발급 안내를 추가한다.

## 검증

- 단위 테스트에서 attempts 증가 Lua가 키 한 개와 TTL 밀리초 값을 사용하고 결과를 반환하는지 검증한다.
- 기존 서비스·컨트롤러·Redis 저장소 테스트를 유지한다.
- 전체 Gradle 테스트를 실행한다.
- `git diff --check`와 `git status --short`로 migration 포함 여부를 확인한다.

## 제외 범위

- Redis와 MySQL을 아우르는 보상 트랜잭션
- 연결 해제 API
- INACTIVE 관계 재활성화 구현
- 성공 후 attempts 키 즉시 삭제
