-- ============================================================
-- 정상 시드 데이터 (렌네이밍된 테이블명 기준)
-- teenymoney_schema_renamed.sql 적용 후 실행
-- 전부 성공해야 정상 (하나라도 실패하면 제약조건에 버그가 있는 것)
-- ============================================================

-- T_MBR_INFO_M: 부모 1, 자녀 2 (부모 1명 고정 구조)
--
-- 아래 세 값은 인증 API(하위3)의 규칙에 맞춘 것이다. 임의로 되돌리지 말 것.
--
--  1) phone_number 는 숫자만 저장한다.
--     서버가 가입 시 하이픈을 제거해 저장하므로 여기에 하이픈을 넣으면, 같은 번호인데
--     문자열이 달라져 사전 중복 검사와 UNIQUE 제약을 둘 다 통과한다. 즉 같은 번호로
--     계정이 두 개 만들어진다. 표시용 하이픈은 프론트가 붙인다.
--
--  2) password 는 실제 BCrypt 해시다. 평문 자리표시자를 쓰면 로그인이 불가능하다.
--     BCryptPasswordEncoder.matches() 가 해시 형식을 보고 즉시 false를 반환하기 때문이다.
--     평문: Local1234!   (세 회원 공통)
--     해시를 새로 뽑으려면:
--       ./gradlew test --tests "*TokenPrinterTest.printSeedPasswordHash" --rerun-tasks -i
--     BCrypt는 salt가 매번 달라 실행마다 값이 바뀐다. 어느 쪽이든 유효하다.
--
--  3) PARENT 의 teeny_score 는 NULL 이다. 컬럼을 생략하면 DB 기본값 600이 들어간다.
--     등급 판정이 T_TNY_GRADE_A 를 BETWEEN 으로 조회하므로 600이면 부모가
--     '양호'(600~799)로 매칭되어 우대금리 0.20%와 오늘만허용 3회를 받는다.
--     스키마 주석도 "(자녀만 사용)"이고 CHECK 가 IS NULL 을 허용한다.
INSERT INTO `T_MBR_INFO_M` (`id`, `role`, `name`, `birth_date`, `phone_number`, `email`, `password`, `teeny_score`, `customer_key`, `status`)
VALUES (1, 'PARENT', '김부모', '1985-03-11', '01011111111', 'parent1@test.com',
        '$2a$10$Ii6qH9kVC2z.mkEdiVas9.dN9yr/wZXPoSUgExNjp7N9Dra8avcSy',
        NULL, 'toss-customer-uuid-1', 'ACTIVE');

-- payment_password 는 아직 자리표시자다. 결제 비밀번호는 결제 도메인 이슈 범위이고
-- 인증 API가 읽지 않으므로 그대로 둔다. 결제 도메인 작업 시 실제 해시로 교체한다.
--
-- 자녀2의 teeny_score 는 610 이다. 600(기본값)이 아니라 610인 이유는 아래 T_TNY_SCOREHIST_H 에
-- +10 이력이 있고 그 행의 score_after 가 610이기 때문이다. 이 컬럼은 이력의 최신 score_after 를
-- 캐시한 값이라, 둘이 어긋나면 "점수 이벤트 적용 후 member.teeny_score == 최신 score_after" 를
-- 검증하는 테스트가 시작부터 깨진 값을 물고 간다. 한쪽을 바꾸면 반드시 다른 쪽도 바꾼다.
-- 610은 여전히 '양호'(600~799) 구간이라 우대금리 0.20%p 가정은 그대로 유지된다.
INSERT INTO `T_MBR_INFO_M` (`id`, `role`, `name`, `birth_date`, `phone_number`, `email`, `password`, `payment_password`, `teeny_score`, `status`)
VALUES
(2, 'CHILD', '김첫째', '2013-05-20', '01022222222', 'child1@test.com',
 '$2a$10$Ii6qH9kVC2z.mkEdiVas9.dN9yr/wZXPoSUgExNjp7N9Dra8avcSy', 'hashed_pay_pw', 610, 'ACTIVE'),
(3, 'CHILD', '김둘째', '2015-09-02', '01033333333', 'child2@test.com',
 '$2a$10$Ii6qH9kVC2z.mkEdiVas9.dN9yr/wZXPoSUgExNjp7N9Dra8avcSy', 'hashed_pay_pw', 600, 'ACTIVE');

-- T_MBR_CONN_R: 부모-자녀 연동 (부모 1명 고정이므로 자녀당 1건)
INSERT INTO `T_MBR_CONN_R` (`id`, `parent_id`, `child_id`, `status`)
VALUES
(1, 1, 2, 'ACTIVE'),
(2, 1, 3, 'ACTIVE');

-- T_TNY_GRADE_A: 참조용 상수 테이블
INSERT INTO `T_TNY_GRADE_A` (`grade_id`, `grade_name`, `min_score`, `max_score`, `bonus_rate`, `monthly_override_limit`, `color`)
VALUES
(1, '회복필요', 0, 199, 0.00, 0, '#FF4D4D'),
(2, '주의', 200, 399, 0.00, 1, '#FF9F40'),
(3, '보통', 400, 599, 0.10, 2, '#FFD400'),
(4, '양호', 600, 799, 0.20, 3, '#4CAF50'),
(5, '우수', 800, 1000, 0.30, 5, '#2196F3');

-- T_MCC_CTGR_C / T_MCC_CODE_C
INSERT INTO `T_MCC_CTGR_C` (`id`, `name`, `default_policy`)
VALUES
(1, '편의점', 'ALLOW'),
(2, 'PC방', 'WATCH'),
(3, '유흥주점', 'BLOCK');

INSERT INTO `T_MCC_CODE_C` (`id`, `merchant_category_id`, `name`)
VALUES
('MCC001', 1, 'CU 편의점'),
('MCC002', 2, '스타PC방');

-- T_MCC_POLICY_M: 자녀 2에게 PC방 커스텀 정책 (부모 1명 고정 -> child_id+category만 UNIQUE)
INSERT INTO `T_MCC_POLICY_M` (`id`, `parent_id`, `child_id`, `merchant_category_id`, `policy`)
VALUES (1, 1, 2, 2, 'BLOCK');

-- T_WLT_BASE_M: 회원 지갑 3개(부모1 + 자녀2) + 자녀2의 예금/적금 전용 지갑 2개
-- (member_id UNIQUE가 아니라 INDEX로 수정되어 있어서 한 회원이 지갑 여러 개 가져도 정상 통과)
--
-- ★ 여기 balance 는 임의의 값이 아니라 이 파일 아래쪽 T_WLT_HIST_H 의 마지막 balance_after 다.
--   잔액조회 API는 이 컬럼을, 거래내역 API는 T_WLT_HIST_H 를 읽으므로 둘이 어긋나면
--   같은 화면 두 곳이 다른 금액을 보여준다. 아래 이체/충전/결제를 하나라도 손대면
--   T_WLT_HIST_H 와 이 다섯 값을 같이 다시 계산해야 한다. 근거는 파일 끝 "잔액 검증" 주석 참고.
--
--   1 부모     500,000 +9,000(대출상환 수취)                        = 509,000
--   2 자녀2    200,000 -100,000(적금) -50,000(예금) +50,000(충전) -3,500(결제) = 96,500
--   3 자녀3     10,000 -9,000(대출상환)                             =   1,000
--   4 예금            0 +50,000(예치)                               =  50,000
--   5 적금            0 +100,000(1회차 납입)                        = 100,000
INSERT INTO `T_WLT_BASE_M` (`id`, `member_id`, `balance`, `type`)
VALUES
(1, 1, 509000, 'MEMBER'),
(2, 2, 96500, 'MEMBER'),
(3, 3, 1000, 'MEMBER'),
(4, 2, 50000, 'DEPOSIT'),
(5, 2, 100000, 'SAVING');

-- T_PAY_METHOD_M: 부모1의 카드/계좌
INSERT INTO `T_PAY_METHOD_M` (`id`, `parent_id`, `billing_key`, `type`, `card_company`, `masked_card_number`, `is_primary`, `status`)
VALUES (1, 1, 'billing-key-card-001', 'CARD', '신한카드', '1234-56**-****-7890', TRUE, 'ACTIVE');

INSERT INTO `T_PAY_METHOD_M` (`id`, `parent_id`, `billing_key`, `type`, `account_bank_name`, `account_number`, `is_primary`, `status`)
VALUES (2, 1, 'billing-key-account-001', 'ACCOUNT', '카카오뱅크', '3333-**-*****1', FALSE, 'ACTIVE');

-- T_DPT_PROD_M / T_SVG_PROD_M / T_LON_PROD_M 상품
INSERT INTO `T_DPT_PROD_M` (`id`, `name`, `rate_1m`, `rate_3m`, `rate_6m`, `rate_12m`, `early_termination_rate`, `min_amount`, `max_amount`, `min_teeny_score`)
VALUES (1, '티니 자유예금', 1.5, 2.0, 2.5, 3.0, 0.5, 10000, 5000000, 0);

INSERT INTO `T_SVG_PROD_M` (`id`, `name`, `savings_type`, `interest_calculation_type`, `rate_12m`, `early_termination_rate`, `min_month_amount`, `max_month_amount`, `min_teeny_score`)
VALUES
(1, '티니 정기적금', 'FIXED', 'SIMPLE', 4.0, 1.0, 10000, 500000, 400),
(2, '티니 자유적금', 'FREE', 'SIMPLE', 3.5, 1.0, 1000, 500000, 0);

INSERT INTO `T_LON_PROD_M` (`id`, `name`, `base_rate`, `late_fee_rate`, `repayment_type`, `min_amount`, `max_amount`, `min_teeny_score`)
VALUES (1, '티니 새싹 대출', 5.0, 8.0, 'EQUAL_PRINCIPAL_INTEREST', 10000, 200000, 300);

-- T_DPT_ENROLL_M: 자녀2가 예금 가입, 승인 완료 상태
-- ACTIVE 예금은 반드시 예치금이 들어와 있어야 한다. 아래 transfer 3 이 그 입금이고
-- 예치액 50,000원은 상품 min_amount(10,000) 이상이다. 이 이체 없이 wallet 4 를 0으로 두면
-- 애초에 개설될 수 없는 예금이 되고, 이자 계산 로직을 돌려도 0이 나와 조용히 통과한다.
INSERT INTO `T_DPT_ENROLL_M`
	(`id`, `deposit_id`, `child_id`, `parent_id`, `wallet_id`, `applied_rate`, `applied_early_termination_rate`, `term_months`, `start_date`, `maturity_date`, `status`)
VALUES (1, 1, 2, 1, 4, 3.2, 0.5, 12, '2026-07-01', '2027-07-01', 'ACTIVE');

-- T_WLT_TRF_L: 예금 가입 예치금 이체 (자녀2 지갑 -> 예금 지갑), 가입 확정일과 같은 날
INSERT INTO `T_WLT_TRF_L` (`id`, `from_wallet_id`, `to_wallet_id`, `idempotency_key`, `amount`, `type`, `status`, `created_at`)
VALUES (3, 2, 4, UUID(), 50000, 'DEPOSIT', 'COMPLETED', '2026-07-01 09:00:00');

-- T_SVG_ENROLL_M: 자녀2가 정기적금 가입
-- start_date 가 2026-06-25 인 이유: payment_day 25 기준으로 1회차 2026-06-25(납입),
-- 2회차 2026-07-25(미납), 3회차 2026-08-25(아직 미도래) 가 되어야 아래 MISSED 행이 성립한다.
-- start_date 를 7/25로 두면 2회차 만기일이 미래라, 연체 배치가 상태를 재계산하는 순간 MISSED 가 뒤집힌다.
INSERT INTO `T_SVG_ENROLL_M`
	(`id`, `saving_id`, `child_id`, `parent_id`, `wallet_id`, `applied_rate`, `applied_early_termination_rate`, `term_months`, `monthly_amount`, `payment_day`, `auto_transfer`, `start_date`, `maturity_date`, `status`)
VALUES (1, 1, 2, 1, 5, 4.2, 1.0, 12, 100000, 25, TRUE, '2026-06-25', '2027-06-25', 'ACTIVE');

-- T_LON_ENROLL_M: 자녀3가 대출 실행 후 1회차 상환까지 마친 상태
--
-- outstanding_principal 과 paid_count 는 아래 T_LON_REPAYHIST_H 를 반영한 캐시 컬럼이다.
-- 컬럼 주석이 각각 '남은 원금 (상환시 감소 캐시)', '성공적으로 지불한 횟수'이므로
-- 1회차에서 원금 8,000을 갚았으면 100,000 - 8,000 = 92,000 이고 paid_count 는 1이다.
-- 실행 직후(상환 이력 0건) 상태를 원하면 아래 상환 이력과 transfer 2 를 같이 지워야 한다.
--
-- applied_rate / applied_late_fee_rate 는 상품의 base_rate / late_fee_rate 를 그대로 스냅샷한다.
-- 예적금은 등급 우대금리(bonus_rate)라는 문서화된 가산 규칙이 있어 3.0+0.20, 4.0+0.20 이지만
-- 대출에는 그런 규칙이 없다. 근거 없이 +0.5 를 얹으면 금리 산출 로직의 기대값을 만들 수 없다.
INSERT INTO `T_LON_ENROLL_M`
	(`id`, `loan_id`, `parent_id`, `child_id`, `principal_amount`, `outstanding_principal`, `overdue_interest`, `applied_rate`, `applied_late_fee_rate`, `auto_transfer`, `payment_day`, `paid_count`, `term_months`, `start_date`, `maturity_date`, `status`)
VALUES (1, 1, 1, 3, 100000, 92000, 0, 5.0, 8.0, TRUE, 25, 1, 12, '2026-07-01', '2027-07-01', 'ACTIVE');

-- T_WLT_TRF_L: 적금 1회차 자동이체 성공 건
INSERT INTO `T_WLT_TRF_L` (`id`, `from_wallet_id`, `to_wallet_id`, `idempotency_key`, `amount`, `type`, `status`, `created_at`)
VALUES (1, 2, 5, UUID(), 100000, 'SAVING', 'COMPLETED', '2026-06-25 09:00:00');

-- T_SVG_PAYHIST_H: 위 transfer와 연결된 1회차 납입 완료
INSERT INTO `T_SVG_PAYHIST_H`
	(`id`, `saving_enrollment_id`, `transfer_id`, `installment_no`, `amount`, `paid_amount`, `status`, `paid_at`)
VALUES (1, 1, 1, 1, 100000, 100000, 'PAID', '2026-06-25 09:00:00');

-- T_SVG_PAYHIST_H: 2회차(2026-07-25 만기)는 잔액부족으로 시도 자체가 없어 MISSED, transfer_id NULL
-- 실제로 그 시점 자녀2 지갑은 50,000원이라 100,000원을 뺄 수 없었다 (아래 T_WLT_HIST_H 참고).
-- 3회차(2026-08-25)는 아직 만기가 오지 않았고, 이 테이블에 '미도래' 상태가 없으므로 행 자체를 넣지 않는다.
INSERT INTO `T_SVG_PAYHIST_H`
	(`id`, `saving_enrollment_id`, `transfer_id`, `installment_no`, `amount`, `paid_amount`, `status`)
VALUES (2, 1, NULL, 2, 100000, 0, 'MISSED');

-- T_WLT_TRF_L: 대출 1회차 상환 성공 건 (payment_day 25 -> 2026-07-25)
INSERT INTO `T_WLT_TRF_L` (`id`, `from_wallet_id`, `to_wallet_id`, `idempotency_key`, `amount`, `type`, `status`, `created_at`)
VALUES (2, 3, 1, UUID(), 9000, 'LOAN', 'COMPLETED', '2026-07-25 09:00:00');

-- T_LON_REPAYHIST_H: 위 transfer와 연결된 1회차 상환 완료 (원금 8,000 + 이자 1,000 = 이체액 9,000)
INSERT INTO `T_LON_REPAYHIST_H`
	(`id`, `loan_enrollment_id`, `transfer_id`, `installment_no`, `principal_amount`, `paid_principal_amount`, `interest_amount`, `paid_interest_amount`, `status`, `paid_at`)
VALUES (1, 1, 2, 1, 8000, 8000, 1000, 1000, 'PAID', '2026-07-25 09:00:00');

-- T_WLT_CHARGE_L: 2회차 미납을 본 부모1이 카드로 자녀2 지갑에 충전
INSERT INTO `T_WLT_CHARGE_L` (`id`, `wallet_id`, `payment_method_id`, `idempotency_key`, `amount`, `status`, `order_id`, `payment_key`, `created_at`)
VALUES (1, 2, 1, UUID(), 50000, 'SUCCESS', 'charge-order-0001', 'toss-payment-key-charge-0001', '2026-07-28 20:00:00');

-- T_PAY_TRAN_L: 자녀2 QR결제 성공 건 (편의점 카테고리, default_policy ALLOW 라 applied_policy 도 ALLOW)
INSERT INTO `T_PAY_TRAN_L` (`id`, `wallet_id`, `category_id`, `idempotency_key`, `applied_policy`, `amount`, `status`, `order_id`, `payment_key`, `created_at`)
VALUES (1, 2, 1, UUID(), 'ALLOW', 3500, 'SUCCESS', 'order-0001', 'toss-payment-key-0001', '2026-07-30 18:00:00');

-- ============================================================
-- T_WLT_HIST_H: 위의 모든 이체/충전/결제를 양쪽 지갑에 빠짐없이 반영한 원장
--
-- ★ 규칙: COMPLETED 이체 한 건은 반드시 두 행을 만든다 (보낸 지갑 DEBIT + 받은 지갑 CREDIT).
--   한쪽만 넣으면 이체는 완료됐는데 받는 지갑 잔액이 그대로인 상태가 되고,
--   이체와 원장을 조인하는 정합성 조회에서 고아 행으로 잡힌다.
--
-- 시간순 원장 (id 순서 = created_at 순서):
--
--   지갑2(자녀2)  시작 200,000
--     06-25  적금 1회차 이체   -100,000 -> 100,000   (transfer 1)
--     07-01  예금 예치금 이체   -50,000 ->  50,000   (transfer 3)
--     07-25  적금 2회차 시도 실패 (잔액 50,000 < 100,000) -> 원장 기록 없음, MISSED 행만 남음
--     07-28  카드 충전          +50,000 -> 100,000   (charge 1)
--     07-30  편의점 결제         -3,500 ->  96,500   (payment 1)
--
--   지갑5(적금)   0 +100,000 -> 100,000
--   지갑4(예금)   0  +50,000 ->  50,000
--   지갑3(자녀3)  10,000 -9,000 -> 1,000   (transfer 2, 대출 1회차 상환)
--   지갑1(부모)  500,000 +9,000 -> 509,000 (transfer 2 수취)
--
-- 각 지갑의 마지막 balance_after 가 T_WLT_BASE_M.balance 와 같아야 한다.
-- ============================================================
INSERT INTO `T_WLT_HIST_H` (`id`, `wallet_id`, `payment_id`, `transfer_id`, `charge_id`, `direction`, `amount`, `balance_after`, `description`, `created_at`)
VALUES
(1, 2, NULL, 1, NULL, 'DEBIT', 100000, 100000, '적금 1회차 자동이체', '2026-06-25 09:00:00'),
(2, 5, NULL, 1, NULL, 'CREDIT', 100000, 100000, '적금 1회차 납입', '2026-06-25 09:00:00'),
(3, 2, NULL, 3, NULL, 'DEBIT', 50000, 50000, '예금 가입 예치', '2026-07-01 09:00:00'),
(4, 4, NULL, 3, NULL, 'CREDIT', 50000, 50000, '예금 예치금 입금', '2026-07-01 09:00:00'),
(5, 3, NULL, 2, NULL, 'DEBIT', 9000, 1000, '대출 1회차 상환', '2026-07-25 09:00:00'),
(6, 1, NULL, 2, NULL, 'CREDIT', 9000, 509000, '대출 1회차 상환 수취', '2026-07-25 09:00:00'),
(7, 2, NULL, NULL, 1, 'CREDIT', 50000, 100000, '카드 충전', '2026-07-28 20:00:00'),
(8, 2, 1, NULL, NULL, 'DEBIT', 3500, 96500, '편의점 결제', '2026-07-30 18:00:00');

-- T_QST_BASE_M: 부모1이 자녀2에게 퀘스트 생성
INSERT INTO `T_QST_BASE_M`
	(`id`, `parent_id`, `child_id`, `title`, `deadline`, `is_teeny_score`, `reward_amount`, `reward_teeny_score`, `status`)
VALUES (1, 1, 2, '방 청소하기', '2026-08-05 23:59:59', TRUE, 1000, 10, 'IN_PROGRESS');

-- T_QST_VERIFY_L: 자녀2가 인증 제출
INSERT INTO `T_QST_VERIFY_L` (`id`, `quest_id`, `image_url`, `content`, `status`)
VALUES (1, 1, 'https://cdn.test.com/quest/1.jpg', '깨끗하게 치웠어요!', 'PENDING');

-- T_TNY_SCOREHIST_H: 적금 1회차 납입 성공으로 자녀2 점수 +10 (600 -> 610)
--
-- 이 행을 퀘스트에 걸지 않는 이유: 위 퀘스트 1은 아직 IN_PROGRESS 이고 인증도 PENDING 이다.
-- 승인되지 않은 퀘스트에 보상 점수가 이미 지급된 상태로 두면, 퀘스트 승인 로직을 이 시드로
-- 테스트할 때 점수가 두 번 올라가는 것을 정상으로 착각하게 된다. 퀘스트 1은 '승인 대기' 케이스로 남긴다.
-- score_after 610 은 T_MBR_INFO_M 의 자녀2 teeny_score 와 반드시 같아야 한다.
INSERT INTO `T_TNY_SCOREHIST_H` (`id`, `child_id`, `amount`, `score_after`, `description`, `reference_type`, `reference_id`, `created_at`)
VALUES (1, 2, 10, 610, '적금납입성공', 'SAVING_ENROLLMENT', 1, '2026-06-25 09:00:00');

-- T_TDP_REQ_L + T_TDP_REQCTGR_R
--
-- expired_at 은 컬럼 주석대로 '당일 자정'이라 실행 시점 기준으로 계산한다. 고정 날짜를 박으면
-- 날이 지나는 순간 "이미 만료됐는데 status 는 PENDING" 인 행이 되고, 스키마에 그 상태를 위한
-- EXPIRED 가 따로 있으므로 만료 배치가 닫았어야 할 행을 대기 목록 조회가 계속 집어 온다.
INSERT INTO `T_TDP_REQ_L` (`id`, `parent_id`, `child_id`, `reason`, `status`, `expired_at`)
VALUES (1, 1, 3, '친구 생일선물 사려고요', 'PENDING', TIMESTAMP(CURDATE(), '23:59:59'));

-- 요청 대상은 PC방(2)이다. 편의점(1)은 default_policy 가 ALLOW 라 승인해도 바뀌는 게 없어
-- 이 기능의 목적(WATCH/BLOCK 을 하루만 푸는 것)을 시드로 검증할 수 없다.
-- 자녀3은 T_MCC_POLICY_M 에 커스텀 정책이 없어 PC방에 기본값 WATCH 가 적용된 상태다.
INSERT INTO `T_TDP_REQCTGR_R` (`today_permission_id`, `merchant_category_id`)
VALUES (1, 2);

-- T_ALW_SCHEDULE_M: 부모1 -> 자녀3, 매달 5일 지급
INSERT INTO `T_ALW_SCHEDULE_M` (`id`, `parent_id`, `child_id`, `amount`, `cycle_type`, `payment_day`, `next_payment_date`)
VALUES (1, 1, 3, 20000, 'MONTHLY', 5, '2026-08-05');

-- T_NTF_NOTI_L: 위 결제(payment 1) 직후 부모에게 나간 알림
INSERT INTO `T_NTF_NOTI_L` (`id`, `member_id`, `type`, `content`, `reference_type`, `reference_id`, `created_at`)
VALUES (1, 1, 'PAYMENT', '자녀2가 3,500원을 결제했어요', 'PAYMENT', 1, '2026-07-30 18:00:01');

-- ============================================================
-- 정합성 검증
--
-- 위 INSERT가 전부 성공해도 그건 제약조건을 통과했다는 뜻일 뿐이다. 제약조건은
-- "이체는 COMPLETED인데 받는 지갑 잔액은 그대로" 같은 의미적 모순을 잡지 못한다.
-- 아래 쿼리는 그런 모순만 골라 뽑는다. 결과가 Empty set 이어야 정상이다.
-- 행이 하나라도 나오면 그 데이터로 만든 테스트는 시작부터 틀린 값을 기대하게 된다.
--
-- 실행: mysql -u <user> -p <db> < sql/seed/01_seed_valid_data.sql
-- ============================================================
(SELECT '지갑 잔액이 원장 마지막 balance_after 와 다름' AS broken_rule,
        CONCAT('wallet ', w.id, ': balance=', w.balance, ', 원장=', h.balance_after) AS detail
 FROM `T_WLT_BASE_M` w
 JOIN `T_WLT_HIST_H` h ON h.wallet_id = w.id
 WHERE h.id = (SELECT MAX(h2.id) FROM `T_WLT_HIST_H` h2 WHERE h2.wallet_id = w.id)
   AND w.balance <> h.balance_after)
UNION ALL
-- 완료된 이체는 보낸 지갑 DEBIT + 받은 지갑 CREDIT 두 행을 만든다. 한쪽만 있으면 원장에 구멍이 난다.
(SELECT '완료된 이체의 원장 행이 2건이 아님',
        CONCAT('transfer ', t.id, ': 원장 ', COUNT(h.id), '건')
 FROM `T_WLT_TRF_L` t
 LEFT JOIN `T_WLT_HIST_H` h ON h.transfer_id = t.id
 WHERE t.status = 'COMPLETED'
 GROUP BY t.id
 HAVING COUNT(h.id) <> 2)
UNION ALL
-- outstanding_principal 과 paid_count 는 상환 이력을 요약한 캐시일 뿐이다.
(SELECT '대출 캐시 컬럼이 상환 이력과 불일치',
        CONCAT('loan ', l.id,
               ': outstanding=', l.outstanding_principal,
               ' 기대=', l.principal_amount - COALESCE(SUM(r.paid_principal_amount), 0),
               ', paid_count=', l.paid_count,
               ' 기대=', COALESCE(SUM(r.status = 'PAID'), 0))
 FROM `T_LON_ENROLL_M` l
 LEFT JOIN `T_LON_REPAYHIST_H` r ON r.loan_enrollment_id = l.id
 GROUP BY l.id, l.principal_amount, l.outstanding_principal, l.paid_count
 HAVING l.outstanding_principal <> l.principal_amount - COALESCE(SUM(r.paid_principal_amount), 0)
     OR l.paid_count <> COALESCE(SUM(r.status = 'PAID'), 0))
UNION ALL
-- teeny_score 도 점수 이력의 최신 score_after 를 캐시한 값이다.
(SELECT '자녀 teeny_score 가 최신 점수 이력과 다름',
        CONCAT('member ', m.id, ': teeny_score=', m.teeny_score, ', 최신 이력=', s.score_after)
 FROM `T_MBR_INFO_M` m
 JOIN `T_TNY_SCOREHIST_H` s ON s.child_id = m.id
 WHERE s.id = (SELECT MAX(s2.id) FROM `T_TNY_SCOREHIST_H` s2 WHERE s2.child_id = m.id)
   AND m.teeny_score <> s.score_after)
UNION ALL
-- 원금이 들어오지 않은 예적금은 애초에 개설될 수 없다. 이자 계산이 조용히 0을 반환한다.
(SELECT 'ACTIVE 예금인데 예치금이 0',
        CONCAT('deposit enrollment ', d.id, ', wallet ', d.wallet_id)
 FROM `T_DPT_ENROLL_M` d
 JOIN `T_WLT_BASE_M` w ON w.id = d.wallet_id
 WHERE d.status = 'ACTIVE' AND w.balance = 0)
UNION ALL
(SELECT 'PAID 납입 이력이 있는데 적금 지갑이 0',
        CONCAT('saving enrollment ', e.id, ', wallet ', e.wallet_id)
 FROM `T_SVG_ENROLL_M` e
 JOIN `T_WLT_BASE_M` w ON w.id = e.wallet_id
 WHERE w.balance = 0
   AND EXISTS (SELECT 1 FROM `T_SVG_PAYHIST_H` p
               WHERE p.saving_enrollment_id = e.id AND p.status = 'PAID'))
UNION ALL
-- 스키마에 EXPIRED 상태가 따로 있으므로, 만료 지난 PENDING 은 배치가 닫았어야 할 행이다.
(SELECT 'PENDING 인데 만료 시각이 이미 지난 오늘만허용 요청',
        CONCAT('today permission ', id, ': expired_at=', expired_at)
 FROM `T_TDP_REQ_L`
 WHERE status = 'PENDING' AND expired_at < NOW())
UNION ALL
-- 승인 전 퀘스트에 보상 점수가 이미 있으면, 승인 로직이 점수를 두 번 올려도 정상으로 보인다.
(SELECT '승인되지 않은 퀘스트에 점수 이력이 있음',
        CONCAT('quest ', q.id, ' (', q.status, '), score history ', s.id)
 FROM `T_TNY_SCOREHIST_H` s
 JOIN `T_QST_BASE_M` q ON q.id = s.reference_id
 WHERE s.reference_type = 'QUEST' AND q.status <> 'COMPLETED')
UNION ALL
-- 만기가 오지 않은 회차를 MISSED 로 두면 연체 배치가 상태를 재계산하는 순간 뒤집힌다.
(SELECT '아직 만기가 오지 않은 회차가 MISSED',
        CONCAT('saving payment ', p.id, ': ', p.installment_no, '회차 만기 ',
               DATE_ADD(e.start_date, INTERVAL (p.installment_no - 1) MONTH))
 FROM `T_SVG_PAYHIST_H` p
 JOIN `T_SVG_ENROLL_M` e ON e.id = p.saving_enrollment_id
 WHERE p.status = 'MISSED'
   AND DATE_ADD(e.start_date, INTERVAL (p.installment_no - 1) MONTH) > CURDATE());
