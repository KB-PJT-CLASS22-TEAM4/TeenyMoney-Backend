# V017 최종 스키마용 로컬 시드 데이터 설계

## 목표

빈 로컬 MySQL 8 데이터베이스에 기준 스키마와 V001부터 V017까지의 마이그레이션을 먼저 적용한 뒤, `01_seed_valid_data.sql`, `02_seed_money_report_demo.sql`, `03_validate_money_report_demo.sql`을 순서대로 실행할 수 있게 한다. V015는 존재하지 않으며 `V006_1__seed_teeny_grade_reference_data.sql`은 번호 순서에 포함한다.

기존 세 파일의 회원, MCC 기준 데이터 1,611건, 지갑, 결제, 예금, 적금, 대출, 퀘스트, 머니 리포트 시나리오와 정합성 검증은 모두 유지한다.

## 실행 순서

1. `sql/schema/teenymoney_schema_renamed.sql`
2. `sql/migration/V001__create_member_agreements.sql`
3. V002부터 V014까지 번호 순서대로 적용한다. V006 다음에는 V006_1을 적용한다.
4. `sql/migration/V016__add_quest_reward_transfer_type.sql`
5. `sql/migration/V017__allow_optional_quest_rejection_reason.sql`
6. `sql/seed/01_seed_valid_data.sql`
7. `sql/seed/02_seed_money_report_demo.sql`
8. `sql/seed/03_validate_money_report_demo.sql`

시드는 빈 로컬 DB에 한 번 실행하는 용도다. 반복 실행을 위한 UPSERT나 기존 데이터 정리 로직은 추가하지 않는다.

## 파일별 변경

### 01_seed_valid_data.sql

- 실행 순서 안내를 V017 이후 실행으로 변경한다.
- V006_1에서 생성하는 티니등급 기준 데이터는 중복 삽입하지 않는다.
- 자녀 회원에 V013의 `applied_grade_id`와 `grade_applied_at`을 최종 등급 구간에 맞게 입력한다.
- 예금과 적금 상품 INSERT에서 V014가 삭제한 `min_teeny_score`를 제거한다.
- 예금 상품에 V011의 `interest_calculation_type`을 입력한다.
- 기존 대출 상품을 새로 만들지 않고 V010이 만든 등급 기반 대출 상품을 조회해 가입 데이터에서 참조한다.
- 대출 가입의 적용 금리는 참조 상품과 일치시킨다.
- 기존 MCC 21개와 MCC 코드 1,611건 및 나머지 기능 데이터와 검증 쿼리는 유지한다.

### 02_seed_money_report_demo.sql

- 실행 순서 안내를 V017 이후로 단순화한다.
- 머니 리포트 회원, 거래, 원장, 예적금, 대출, 퀘스트, 점수 이력 데이터를 모두 유지한다.
- 대출 상품 조회를 V010의 최종 등급 기반 상품명으로 변경하고 기존 5% 적용 금리와 일치하는 플러스 등급 상품을 사용한다.
- 1회 실행 방지 가드와 마지막 회원 조회의 이메일을 새 주소로 변경한다.

### 03_validate_money_report_demo.sql

- 모든 이메일 동등 비교, IN 조건, LIKE 조건을 새 주소와 도메인에 맞게 변경한다.
- 기존 위반 규칙과 기대값은 유지한다.
- 정상 실행 시 위반 결과가 없어야 한다.

## 이메일 매핑

| 기존 이메일 | 변경 이메일 |
|---|---|
| `parent1@test.com` | `parent1@naver.com` |
| `child1@test.com` | `child1@gmail.com` |
| `child2@test.com` | `child2@gmail.com` |
| `report-parent@test.com` | `report-parent@naver.com` |
| `report-junior@test.com` | `report-junior@gmail.com` |
| `report-teen@test.com` | `report-teen@gmail.com` |
| `report-empty@test.com` | `report-empty@gmail.com` |

이메일 로컬 파트는 기존 로그인 식별 의미를 유지하고 도메인만 변경한다. 시드는 로컬 전용이며 외부 메일 발송을 수행하지 않는다.

## 오류 처리와 검증

- SQL 문법과 최종 컬럼 목록을 정적으로 확인한다.
- 세 시드 파일에 기존 `@test.com` 또는 `@test` 이메일이 남지 않았는지 검색한다.
- 01과 02가 삭제된 `min_teeny_score` 컬럼에 값을 넣지 않는지 확인한다.
- 대출 가입 행이 존재하는 등급 기반 상품을 참조하고 적용 금리가 상품 금리와 일치하는지 확인한다.
- 가능한 경우 빈 MySQL 8 데이터베이스에서 스키마, 전체 마이그레이션, 01, 02, 03을 순서대로 실행한다.
- MySQL 실행 환경이 없으면 정적 검사와 프로젝트 테스트 결과를 기록하고 DB 통합 검증이 수행되지 않았음을 명시한다.

## 범위 제외

- 마이그레이션 파일의 구조 변경
- 시드 반복 실행 지원
- 운영 또는 공용 DB 적용
- 테스트 코드와 API 예시 전반의 이메일 일괄 변경
- 기존 시나리오와 무관한 데이터 추가 또는 삭제
