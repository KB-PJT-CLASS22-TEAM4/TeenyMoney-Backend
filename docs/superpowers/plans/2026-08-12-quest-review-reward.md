# Quest Review and Reward Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 부모가 최신 퀘스트 인증을 승인·반려하고 보상 송금, 티니점수, 인증·퀘스트 상태를 원자적으로 처리한다.

**Architecture:** `QuestReviewService`가 퀘스트 행 잠금부터 송금·점수·상태 갱신까지 하나의 트랜잭션으로 오케스트레이션한다. 송금은 `TransferService.transferInExistingTransaction` 공통 경로가 기존 송금 행·지갑 잠금·원장을 재사용하고, 인증/퀘스트 변경은 기대 상태가 포함된 MyBatis 문장으로 경쟁 요청을 막는다.

**Tech Stack:** Java 17, Spring MVC 5.3, Spring Transaction, MyBatis 3.4, MySQL 8, JUnit 5, Mockito, MockMvc, Gradle

## Global Constraints

- 기준 커밋은 PR #118 HEAD `ccf24d86de3a1e553c3da9d94815a0d453fce0f6`이다.
- 성공 점수는 +3, 최종 실패는 -2이며 월 점수 상한과 점수 퀘스트 개수 제한은 없다.
- 승인 시 송금·원장·점수·인증·퀘스트는 하나의 트랜잭션으로 커밋하거나 모두 롤백한다.
- 최신 `PENDING` 인증만 검토할 수 있고 오래되거나 처리된 인증은 409다.
- 세 번째 반려는 항상 최종 실패다.
- 기존 `TransferService.executeTransfer`의 독립 실행 및 실패 기록 계약은 보존한다.
- 운영 SQL은 자동 적용되지 않으므로 신규 마이그레이션과 전체 스키마를 함께 갱신한다.

---

### Task 1: 퀘스트 점수 정책과 검토 요청 계약

**Files:**
- Create: `src/main/java/com/teenyfin/teenymoney/domain/quest/vo/AfterDeadlineAction.java`
- Create: `src/main/java/com/teenyfin/teenymoney/domain/quest/dto/request/QuestRejectRequestDTO.java`
- Modify: `src/main/java/com/teenyfin/teenymoney/domain/quest/exception/QuestErrorCode.java`
- Modify: `src/main/java/com/teenyfin/teenymoney/domain/teenyscore/vo/TeenyScoreEventCode.java`
- Modify: `src/main/java/com/teenyfin/teenymoney/domain/teenyscore/service/TeenyScorePolicyService.java`
- Test: `src/test/java/com/teenyfin/teenymoney/domain/teenyscore/service/TeenyScorePolicyServiceTest.java`

**Interfaces:**
- Produces: `questCompleted(Long childId, Long questId)` and `questFailed(Long childId, Long questId)`
- Produces: `QuestRejectRequestDTO#getReason/getAfterDeadlineAction/getExtendedDeadline`

- [ ] **Step 1: Write failing quest score policy tests**

```java
@Test
void questCompletedCreatesPlusThreeIdempotentEvent() {
    TeenyScoreChangeRequestDTO request = teenyScorePolicyService.questCompleted(2L, 55L);
    assertEquals(3, request.getAmount());
    assertEquals(TeenyScoreEventCode.QUEST_COMPLETED, request.getEventCode());
    assertEquals("QUEST_COMPLETED:55", request.getEventKey());
    assertEquals("QUEST", request.getReferenceType());
}

@Test
void questFailedCreatesMinusTwoIdempotentEvent() {
    TeenyScoreChangeRequestDTO request = teenyScorePolicyService.questFailed(2L, 55L);
    assertEquals(-2, request.getAmount());
    assertEquals(TeenyScoreEventCode.QUEST_FAILED, request.getEventCode());
    assertEquals("QUEST_FAILED:55", request.getEventKey());
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `.\gradlew.bat test --tests "*TeenyScorePolicyServiceTest"`

Expected: compilation failure because quest event codes and methods do not exist.

- [ ] **Step 3: Add the minimal contracts and policy methods**

```java
public enum AfterDeadlineAction { EXTEND, FAIL }

public TeenyScoreChangeRequestDTO questCompleted(Long childId, Long questId) {
    return request(childId, TeenyScoreEventCode.QUEST_COMPLETED, 3,
            "QUEST_COMPLETED:" + questId, "퀘스트 성공", "QUEST", questId);
}

public TeenyScoreChangeRequestDTO questFailed(Long childId, Long questId) {
    return request(childId, TeenyScoreEventCode.QUEST_FAILED, -2,
            "QUEST_FAILED:" + questId, "퀘스트 최종 실패", "QUEST", questId);
}
```

`QuestRejectRequestDTO`는 선택적인 `@Size(max = 500)` reason과 선택적인 action/deadline을 가진다. reason 누락·공백은 `NULL`로 정규화하고, 조합 검증은 현재 시간과 시도 횟수가 필요한 서비스에서 수행한다.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `.\gradlew.bat test --tests "*TeenyScorePolicyServiceTest"`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/teenyfin/teenymoney/domain/quest src/main/java/com/teenyfin/teenymoney/domain/teenyscore src/test/java/com/teenyfin/teenymoney/domain/teenyscore
git commit -m "feat: add quest review and score contracts"
```

### Task 2: 호출자 트랜잭션 참여형 공통 송금

**Files:**
- Modify: `src/main/java/com/teenyfin/teenymoney/domain/wallet/service/TransferService.java`
- Modify: `src/main/java/com/teenyfin/teenymoney/domain/wallet/vo/TransferType.java`
- Modify: `src/test/java/com/teenyfin/teenymoney/domain/wallet/service/TransferServiceTest.java`
- Create: `sql/migration/V016__add_quest_reward_transfer_type.sql`
- Modify: `sql/schema/teenymoney_schema_renamed.sql`

**Interfaces:**
- Produces: `TransferVO transferInExistingTransaction(Long fromWalletId, Long toWalletId, Long amount, TransferType type, String idempotencyKey)`
- Requires: an active Spring transaction (`Propagation.MANDATORY`)

- [ ] **Step 1: Write a failing transaction contract test**

Add a Spring test that resolves the proxied `TransferService` bean and asserts a call outside a transaction throws `IllegalTransactionStateException`. Add a DB-gated integration test that invokes the method inside `TransactionTemplate`, throws after it returns, and verifies transfer/wallet/history changes roll back.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `.\gradlew.bat test --tests "*TransferServiceTest"`

Expected: compilation failure because `transferInExistingTransaction` and `QUEST_REWARD` do not exist.

- [ ] **Step 3: Refactor pending creation and add the mandatory path**

```java
@Transactional
public TransferVO createPendingTransfer(...) {
    return createOrLoadPendingTransfer(...);
}

@Transactional(propagation = Propagation.MANDATORY)
public TransferVO transferInExistingTransaction(...) {
    TransferVO pending = createOrLoadPendingTransfer(...);
    return transferExecutor.lockAndMove(pending.getId());
}
```

The private helper retains all validation and duplicate-key behavior. The mandatory path does not call `TransferFailureRecorder`; exceptions propagate so the caller transaction rolls back.

- [ ] **Step 4: Add SQL type support**

`V015` drops and recreates `CK_WLT_TRF_L_TYPE` with `QUEST_REWARD`. Update the schema comment and CHECK list to the same values.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run: `.\gradlew.bat test --tests "*TransferServiceTest" --tests "*WalletLedgerServiceTest"`

Expected: unit/context tests pass; DB-gated tests pass when credentials exist or report skipped when absent.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/teenyfin/teenymoney/domain/wallet src/test/java/com/teenyfin/teenymoney/domain/wallet sql
git commit -m "feat: add transaction-participating reward transfer"
```

### Task 3: 최신 인증 잠금과 검토 결과 SQL

**Files:**
- Modify: `src/main/java/com/teenyfin/teenymoney/domain/quest/mapper/QuestMapper.java`
- Modify: `src/main/resources/com/teenyfin/teenymoney/domain/quest/mapper/QuestMapper.xml`
- Modify: `src/test/java/com/teenyfin/teenymoney/domain/quest/mapper/QuestMapperContextTest.java`

**Interfaces:**
- Produces: `selectLatestVerificationForUpdate(Long questId)`
- Produces: `updateVerificationReview(...)`
- Produces: `updateCompletedByParent(...)`
- Produces: `updateAfterRejectionByParent(...)`

- [ ] **Step 1: Write failing mapper registration and SQL-shape tests**

Assert the new statements are registered. Assert latest verification orders by attempt descending and includes `FOR UPDATE`; review update requires `status = 'PENDING'`; quest updates require `parent_id` and `status = 'PENDING'`.

- [ ] **Step 2: Run mapper test and verify RED**

Run: `.\gradlew.bat test --tests "*QuestMapperContextTest"`

Expected: missing mapped statement failure.

- [ ] **Step 3: Add mapper methods and guarded SQL**

```sql
SELECT ... FROM T_QST_VERIFY_L
WHERE quest_id = #{questId}
ORDER BY attempt_no DESC LIMIT 1 FOR UPDATE

UPDATE T_QST_VERIFY_L
SET status = #{status}, rejection_reason = #{rejectionReason}, reviewed_at = #{reviewedAt}
WHERE id = #{verificationId} AND quest_id = #{questId} AND status = 'PENDING'
```

Quest completion and rejection outcome updates include the expected parent and `PENDING` state.

- [ ] **Step 4: Run mapper test and verify GREEN**

Run: `.\gradlew.bat test --tests "*QuestMapperContextTest"`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/teenyfin/teenymoney/domain/quest/mapper src/main/resources/com/teenyfin/teenymoney/domain/quest/mapper src/test/java/com/teenyfin/teenymoney/domain/quest/mapper
git commit -m "feat: add guarded quest review persistence"
```

### Task 4: 승인 유스케이스

**Files:**
- Create: `src/main/java/com/teenyfin/teenymoney/domain/quest/service/QuestReviewService.java`
- Create: `src/test/java/com/teenyfin/teenymoney/domain/quest/service/QuestReviewServiceTest.java`

**Interfaces:**
- Produces: `void approve(MemberPrincipal principal, Long questId, Long verificationId)`
- Consumes: mapper guarded methods, wallet lookup, mandatory transfer, score policy/change services

- [ ] **Step 1: Write failing approval tests**

Cover cash+score success, reward 0 skip, score disabled skip, insufficient balance propagation, wrong latest ID, reviewed latest verification, non-PENDING quest, and update count conflict. Capture arguments and verify transfer key `QUEST_REWARD:{questId}` and score key `QUEST_COMPLETED:{questId}`.

- [ ] **Step 2: Run service test and verify RED**

Run: `.\gradlew.bat test --tests "*QuestReviewServiceTest"`

Expected: compilation failure because the service does not exist.

- [ ] **Step 3: Implement the minimal transactional approval flow**

`@Transactional` method order: require parent → quest lock → latest verification lock → reward transfer if positive → score if enabled → approve verification → complete quest. Every guarded update must return 1.

- [ ] **Step 4: Run service test and verify GREEN**

Run: `.\gradlew.bat test --tests "*QuestReviewServiceTest"`

Expected: approval tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/teenyfin/teenymoney/domain/quest/service src/test/java/com/teenyfin/teenymoney/domain/quest/service
git commit -m "feat: implement atomic quest approval"
```

### Task 5: 반려 유스케이스

**Files:**
- Modify: `src/main/java/com/teenyfin/teenymoney/domain/quest/service/QuestReviewService.java`
- Modify: `src/test/java/com/teenyfin/teenymoney/domain/quest/service/QuestReviewServiceTest.java`

**Interfaces:**
- Produces: `void reject(MemberPrincipal principal, Long questId, Long verificationId, QuestRejectRequestDTO request)`

- [ ] **Step 1: Write failing rejection state-machine tests**

Cover missing/blank reason normalization to `NULL`, reason length overflow, before-deadline reopen, after-deadline EXTEND, after-deadline FAIL, third rejection forced failure, invalid action combinations, invalid extension bounds, and final-failure score disabled/enabled.

- [ ] **Step 2: Run service test and verify RED**

Run: `.\gradlew.bat test --tests "*QuestReviewServiceTest"`

Expected: rejection tests fail because `reject` is missing.

- [ ] **Step 3: Implement request normalization and state transitions**

Calculate `newRemaining = remainingCount - 1`. If zero, force `FAILED`. Otherwise use current deadline and action rules. When the result is final `FAILED`, persist the remaining count as zero. Record verification rejection before the guarded quest update inside the same transaction. Apply `QUEST_FAILED` score only for final failure and enabled quests.

- [ ] **Step 4: Run service test and verify GREEN**

Run: `.\gradlew.bat test --tests "*QuestReviewServiceTest"`

Expected: all approval and rejection tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/teenyfin/teenymoney/domain/quest/service src/test/java/com/teenyfin/teenymoney/domain/quest/service
git commit -m "feat: implement quest rejection state machine"
```

### Task 6: 부모 API와 전체 상세 응답

**Files:**
- Modify: `src/main/java/com/teenyfin/teenymoney/domain/quest/controller/QuestController.java`
- Modify: `src/test/java/com/teenyfin/teenymoney/domain/quest/controller/QuestControllerTest.java`

**Interfaces:**
- Produces: PATCH approve and reject endpoints
- Consumes: `QuestReviewService`; returns `QuestQueryService.getQuest(...)`

- [ ] **Step 1: Write failing MockMvc tests**

Assert exact routes, parent principal delegation, optional reject reason binding, empty JSON acceptance, reason length validation, and the `QuestDetailResponseDTO` response.

- [ ] **Step 2: Run controller test and verify RED**

Run: `.\gradlew.bat test --tests "*QuestControllerTest"`

Expected: endpoint 404 or compilation failure because controller dependency/methods do not exist.

- [ ] **Step 3: Add controller methods and Swagger docs**

Both methods use `@PreAuthorize("hasRole('PARENT')")`. Approve has no body. Reject uses `@RequestBody @Valid QuestRejectRequestDTO`. Each calls review service and then returns the refreshed detail from query service.

- [ ] **Step 4: Run controller test and verify GREEN**

Run: `.\gradlew.bat test --tests "*QuestControllerTest"`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/teenyfin/teenymoney/domain/quest/controller src/test/java/com/teenyfin/teenymoney/domain/quest/controller
git commit -m "feat: expose parent quest review APIs"
```

### Task 7: 통합 검증과 구현 기록

**Files:**
- Create: `src/test/java/com/teenyfin/teenymoney/domain/quest/service/QuestReviewServiceIntegrationTest.java`
- Create: `docs/issue-104-implementation-report.md`
- Modify: `docs/QUEST_FEATURE_DESIGN.md`

**Interfaces:**
- Verifies the complete issue contract and documents implementation evidence.

- [ ] **Step 1: Add DB-gated integration tests**

Use the repository's `@EnabledIfEnvironmentVariable` pattern. Verify successful approval persists transfer/history/score/status, insufficient balance rolls all back, zero reward creates no transfer, and two concurrent reviews apply once.

- [ ] **Step 2: Run focused quest tests**

Run: `.\gradlew.bat test --tests "com.teenyfin.teenymoney.domain.quest.*"`

Expected: all non-DB tests pass; DB tests pass or skip only because environment variables are absent.

- [ ] **Step 3: Update feature design and write the implementation report**

Record chronological actions, base SHA, each RED/GREEN command, problems, root causes, fixes, final file list, migration instructions, skipped environment-dependent checks, and fresh verification output.

- [ ] **Step 4: Run full verification**

Run:

```powershell
.\gradlew.bat test
.\gradlew.bat war
git diff --check
git status --short
```

Expected: Gradle exit 0, zero failed tests, WAR created, no whitespace errors, only intended report changes uncommitted before final commit.

- [ ] **Step 5: Commit documentation and final integration coverage**

```powershell
git add src/test/java/com/teenyfin/teenymoney/domain/quest/service/QuestReviewServiceIntegrationTest.java docs/QUEST_FEATURE_DESIGN.md docs/issue-104-implementation-report.md
git commit -m "test: verify quest review transaction flow"
```

- [ ] **Step 6: Re-run fresh final verification after commit**

Run: `.\gradlew.bat clean test war`

Expected: exit 0 with no failed tests and `build/libs/ROOT.war` present.
