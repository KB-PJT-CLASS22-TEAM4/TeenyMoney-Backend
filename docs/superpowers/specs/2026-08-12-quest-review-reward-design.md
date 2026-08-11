# 이슈 #104 퀘스트 승인·반려·보상 처리 설계

## 목표

부모가 자녀의 최신 `PENDING` 인증을 승인하거나 반려한다. 승인은 현금 보상 송금, 티니점수 반영, 인증 승인, 퀘스트 완료를 하나의 DB 트랜잭션으로 처리한다. 반려는 시도 횟수와 기한을 기준으로 재도전, 기한 연장, 최종 실패를 결정한다.

## 기준 코드와 범위

- 구현 기준: PR #118의 최신 커밋 `ccf24d86de3a1e553c3da9d94815a0d453fce0f6`
- 기준 브랜치: `103-feature-psh-quest-progress-verification`
- 구현 브랜치: `104-feature-psh-quest-review-reward`
- 포함: 부모 승인·반려 API, 원자적 보상 송금, 퀘스트 티니점수, 상태 전이, Swagger, SQL, 테스트
- 제외: 자동 마감 스케줄러, 알림, 월간 등급 구조, 카드 결제와 퀘스트 승인 결합

PR #118이 아직 `dev`에 병합되지 않았으므로 #104는 해당 PR의 HEAD에서 시작한다. #118 병합 후에는 그 병합 결과에 이 브랜치를 리베이스하거나 체리픽한다.

## 확정 API

### 승인

```http
PATCH /quests/{questId}/verifications/{verificationId}/approve
Authorization: PARENT
```

요청 본문은 없다. 성공하면 갱신된 `QuestDetailResponseDTO` 전체를 반환한다.

### 반려

```http
PATCH /quests/{questId}/verifications/{verificationId}/reject
Authorization: PARENT
Content-Type: application/json
```

```json
{
  "reason": "인증 사진에 완료 결과가 보이지 않아요.",
  "afterDeadlineAction": "EXTEND",
  "extendedDeadline": "2026-08-13T20:00:00"
}
```

- `reason`: 공백 제거 후 1~500자 필수
- 기한 전 첫 번째·두 번째 반려: `afterDeadlineAction`, `extendedDeadline` 모두 없어야 한다.
- 기한 후 첫 번째·두 번째 반려: `afterDeadlineAction` 필수
- `EXTEND`: `extendedDeadline`이 현재보다 미래이고 1년 이내여야 한다.
- `FAIL`: `extendedDeadline`이 없어야 한다.
- 세 번째 반려: 선택값과 관계없이 최종 실패한다. `afterDeadlineAction`과 `extendedDeadline`은 사용하지 않는다.

성공하면 갱신된 `QuestDetailResponseDTO` 전체를 반환한다.

## 아키텍처

`QuestController`는 HTTP 입력과 Swagger만 담당한다. `QuestReviewService`가 승인·반려 유스케이스와 전체 트랜잭션을 소유한다. `QuestMapper`는 퀘스트와 최신 인증 잠금, 인증 검토 결과, 퀘스트 결과 갱신을 담당한다.

보상 송금은 퀘스트 전용 구현을 만들지 않는다. `TransferService`에 호출자의 기존 트랜잭션에 반드시 참여하는 공통 메서드를 추가한다. 이 메서드는 `Propagation.MANDATORY`로 외부 트랜잭션을 강제하고, 송금 행 생성·잠금·지갑 잔액·원장·송금 완료를 호출자 트랜잭션 안에서 처리한다. 기존 수동 용돈용 `createPendingTransfer`와 독립 실행 `executeTransfer`는 변경하지 않는다.

## 공통 잠금과 검증 순서

1. 부모 권한과 유효한 ID를 검증한다.
2. `(questId, parentId)`로 퀘스트를 `FOR UPDATE` 조회한다.
3. 퀘스트가 `PENDING`인지 확인한다.
4. 해당 퀘스트의 최신 인증을 `FOR UPDATE` 조회한다.
5. 최신 인증 ID가 요청 `verificationId`와 같은지, 상태가 `PENDING`인지 확인한다.
6. 검증을 통과한 뒤에만 송금·점수·상태 변경을 실행한다.

같은 퀘스트의 동시 승인·반려는 2번의 퀘스트 행 잠금으로 직렬화된다. 첫 요청이 커밋된 뒤 두 번째 요청은 최신 상태를 다시 읽고 `409`를 반환한다.

## 승인 데이터 흐름

1. 공통 잠금과 검증을 수행한다.
2. `rewardAmount > 0`이면 부모와 자녀의 `MEMBER` 지갑을 조회한다.
3. `TransferType.QUEST_REWARD`와 멱등 키 `QUEST_REWARD:{questId}`로 원자적 송금을 실행한다.
4. `teenyScoreEnabled = true`이면 `QUEST_COMPLETED:{questId}` 이벤트 키로 자녀 점수 `+3`을 반영한다.
5. 인증을 `APPROVED`, `reviewed_at = now`로 갱신한다.
6. 퀘스트를 `COMPLETED`, `ended_at = now`, `updated_at = now`로 갱신한다.
7. 모두 성공하면 커밋한다.

현금 보상이 0원이면 2~3번을 건너뛴다. 부모 잔액 부족을 포함한 어느 단계의 실패도 전체 트랜잭션을 롤백한다. 이 경우 송금 행, 지갑 잔액, 원장, 점수, 인증, 퀘스트가 모두 승인 전 상태로 남는다.

## 반려 상태 전이

인증은 모든 정상 반려에서 `REJECTED`, `rejection_reason`, `reviewed_at`을 기록한다. 새 남은 횟수는 `remainingCount - 1`이다.

| 조건 | 퀘스트 결과 | 점수 |
|---|---|---:|
| 기한 전, 첫 번째·두 번째 반려 | `IN_PROGRESS`, 남은 횟수 감소 | 0 |
| 기한 후, `EXTEND` | 새 기한, `IN_PROGRESS`, 남은 횟수 감소 | 0 |
| 기한 후, `FAIL` | `FAILED`, 남은 횟수 0, `ended_at` 기록 | 티니점수 사용 시 -2 |
| 세 번째 반려 | 항상 `FAILED`, 남은 횟수 0, `ended_at` 기록 | 티니점수 사용 시 -2 |

최종 실패 점수 이벤트 키는 `QUEST_FAILED:{questId}`이다. 반려와 재도전은 점수에 영향을 주지 않는다.

## 티니점수 정책

- 성공: `+3`
- 최종 실패: `-2`
- 월 점수 상한: 없음
- 월 점수 퀘스트 개수 제한: 없음
- 점수 반영 여부는 퀘스트 생성 시 부모가 정하고, 수락 이후 변경할 수 없다.
- `TeenyScorePolicyService`가 퀘스트 완료·실패 요청을 생성하고 `TeenyScoreChangeService`가 기존 이벤트 키 중복 방지와 0~1000 보정을 재사용한다.

`TeenyScoreEventCode`에는 `QUEST_COMPLETED`, `QUEST_FAILED`를 추가한다. 점수 이력 `reference_type`은 `QUEST`, `reference_id`는 `questId`다.

## 송금 인프라와 SQL

- `TransferType`에 `QUEST_REWARD`를 추가한다.
- `T_WLT_TRF_L.type` CHECK 제약에 `QUEST_REWARD`를 추가하는 `V015` 마이그레이션을 작성한다.
- 현재 전체 스키마 파일의 같은 CHECK와 주석도 갱신한다.
- 원장 참조는 기존 `ReferenceType.TRANSFER`와 생성된 `transfer_id`를 사용한다.
- 지갑 잠금은 기존 `TransferExecutor`의 작은 `wallet_id` 우선 규칙을 재사용한다.

## 오류 계약

| 상황 | 상태 | 코드 |
|---|---:|---|
| 퀘스트가 없거나 부모 소유가 아님 | 404 | `QUEST_NOT_FOUND_OR_ACCESS_DENIED` |
| 퀘스트가 `PENDING`이 아님 | 409 | `QUEST_STATUS_CONFLICT` |
| 인증이 최신이 아니거나 이미 처리됨 | 409 | `QUEST_VERIFICATION_CONFLICT` |
| 반려 사유 또는 기한 후 선택 조합이 잘못됨 | 400 | `QUEST_REVIEW_REQUEST_INVALID` |
| 연장 기한이 미래가 아니거나 1년 초과 | 400 | `QUEST_EXTENDED_DEADLINE_INVALID` |
| 부모 지갑 잔액 부족 | 400 | 기존 `INSUFFICIENT_BALANCE` |
| 부모·자녀 MEMBER 지갑 없음 | 404 또는 기존 계약 | 기존 `WALLET_NOT_FOUND` |

매퍼 갱신 결과가 1건이 아니면 경쟁 상태로 간주하고 `QUEST_STATUS_CONFLICT`를 던진다.

## 테스트 전략

### 단위 테스트

- 승인: 현금+점수, 0원 보상, 점수 비활성, 잔액 부족 전파, 오래된 인증, 처리된 인증
- 반려: 기한 전 재도전, 기한 후 연장, 기한 후 실패, 세 번째 강제 실패, 잘못된 요청 조합
- 점수 정책: 완료 +3, 실패 -2, 이벤트 코드·키·참조값
- 원자적 송금: 기존 트랜잭션 필수와 기존 송금 API 보존

### 웹·매퍼 테스트

- 부모 승인·반려 경로, JSON 검증, 전체 상세 반환, 서비스 위임
- 최신 인증 `FOR UPDATE`, 기대 상태 조건, 완료·반려 SQL 필드
- Swagger 애노테이션과 DTO 모델

### DB 통합 테스트

환경변수로 MySQL이 제공될 때 다음을 검증한다.

- 승인 성공의 송금·원장·점수·상태 전체 반영
- 잔액 부족의 전체 롤백과 재승인 가능성
- 동시에 같은 인증을 처리해 보상·점수가 한 번만 반영됨
- 0원 보상에서 송금 행이 생기지 않음

DB가 없을 때 통합 테스트는 기존 저장소 방식대로 스킵하고, 단위·컨텍스트·전체 빌드는 항상 실행한다.

## 완료 기준

- 이슈 #104의 모든 체크리스트가 코드와 테스트로 연결된다.
- 전체 테스트와 WAR 빌드가 성공한다.
- SQL 수동 적용 방법이 `sql/README.md`와 일치한다.
- 구현 순서, 발생한 문제, 해결, 검증 명령과 결과를 별도 작업 기록에 남긴다.
