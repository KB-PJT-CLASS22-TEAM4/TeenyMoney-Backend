# SQL 변경 관리

현재 프로젝트에는 Flyway가 도입되지 않았습니다. 이 디렉터리의 SQL은 자동으로
실행되지 않으며, 적용 대상 환경과 순서를 확인한 담당자가 직접 실행합니다.

## 디렉터리

```text
sql/
├── schema/
├── migration/
└── seed/
```

- `schema`: 승인된 변경이 모두 반영된 현재 전체 스키마
- `migration`: 기존 스키마에 순서대로 적용하는 변경 SQL
- `seed`: 개인 로컬 환경에서만 사용하는 테스트 데이터

## 파일명

```text
schema/teenymoney_schema_renamed.sql
migration/V001__create_member.sql
migration/V002__alter_member_payment_lock.sql
seed/01_seed_valid_data.sql
```

seed는 아래 실행 순서를 지킵니다. FK 참조 때문에 회원 → 연동 → 지갑 순서로
들어가야 합니다.

현재 시드 실행 순서는 다음과 같습니다.

1. `schema/teenymoney_schema_renamed.sql`
2. `seed/01_seed_valid_data.sql`

`01_seed_valid_data.sql`에는 MCC 기준 데이터와 기능 테스트 데이터가 함께 있으며,
기능 데이터는 앞에서 삽입한 업종 카테고리를 이름으로 조회해 참조합니다.

migration 번호는 중복되지 않게 순서대로 증가시킵니다. Flyway를 도입하면 기존
파일의 호환성과 적용 이력을 검토한 뒤 자동 migration으로 전환합니다.

## 적용 원칙

- 로컬 MySQL에서 먼저 실행하고 영향을 확인합니다.
- EC2에는 Pull Request 검토가 끝난 SQL만 반영합니다.
- EC2에 적용한 migration 파일은 수정하거나 삭제하지 않습니다.
- 변경이 필요하면 다음 번호의 migration 파일을 추가합니다.
- 승인된 migration 반영 후 schema 파일도 현재 구조에 맞게 갱신합니다.
- seed는 EC2에 적용하지 않습니다.
- 실제 개인정보, DB 자격증명, 토큰을 SQL에 넣지 않습니다.
- `DROP`, `TRUNCATE`, 컬럼 삭제, 대량 `DELETE`는 팀 확인 없이 실행하지 않습니다.

## Flyway 도입 전 주의사항

현재는 migration 적용 여부를 애플리케이션이 추적하지 않습니다. EC2 반영 시
적용한 파일명, 적용 일시, 담당자를 Pull Request나 배포 기록에 남겨야 합니다.

## 이슈 #104 배포 전 적용

퀘스트 현금 보상은 `T_WLT_TRF_L.type = 'QUEST_REWARD'`를 사용합니다.
애플리케이션 배포 전에 아래 파일을 MySQL에 수동 적용하고 적용 일시와 담당자를
배포 기록에 남깁니다.

```text
sql/migration/V016__add_quest_reward_transfer_type.sql
sql/migration/V017__allow_optional_quest_rejection_reason.sql
```

V016은 기존 송금 유형 CHECK 제약을 같은 이름으로 다시 만들고, V017은 인증 반려 사유를
선택 사항으로 바꿉니다. 기존 데이터나 컬럼은 삭제하지 않습니다.
