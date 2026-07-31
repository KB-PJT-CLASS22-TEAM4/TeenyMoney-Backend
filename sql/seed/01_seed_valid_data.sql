-- ============================================================
-- 정상 시드 데이터 (렌네이밍된 테이블명 기준)
-- teenymoney_schema_renamed.sql 적용 후 실행
-- 전부 성공해야 정상 (하나라도 실패하면 제약조건에 버그가 있는 것)
-- ============================================================

-- T_MBR_INFO_M: 부모 1, 자녀 2 (부모 1명 고정 구조)
INSERT INTO `T_MBR_INFO_M` (`id`, `role`, `name`, `birth_date`, `phone_number`, `email`, `password`, `customer_key`, `status`)
VALUES (1, 'PARENT', '김부모', '1985-03-11', '010-1111-1111', 'parent1@test.com', 'hashed_pw', 'toss-customer-uuid-1', 'ACTIVE');

INSERT INTO `T_MBR_INFO_M` (`id`, `role`, `name`, `birth_date`, `phone_number`, `email`, `password`, `payment_password`, `teeny_score`, `status`)
VALUES
(2, 'CHILD', '김첫째', '2013-05-20', '010-2222-2222', 'child1@test.com', 'hashed_pw', 'hashed_pay_pw', 600, 'ACTIVE'),
(3, 'CHILD', '김둘째', '2015-09-02', '010-3333-3333', 'child2@test.com', 'hashed_pw', 'hashed_pay_pw', 600, 'ACTIVE');

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
INSERT INTO `T_WLT_BASE_M` (`id`, `member_id`, `balance`, `type`)
VALUES
(1, 1, 500000, 'MEMBER'),
(2, 2, 150000, 'MEMBER'),
(3, 3, 10000, 'MEMBER'),
(4, 2, 0, 'DEPOSIT'),
(5, 2, 0, 'SAVING');

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
INSERT INTO `T_DPT_ENROLL_M`
	(`id`, `deposit_id`, `child_id`, `parent_id`, `wallet_id`, `applied_rate`, `applied_early_termination_rate`, `term_months`, `start_date`, `maturity_date`, `status`)
VALUES (1, 1, 2, 1, 4, 3.2, 0.5, 12, '2026-07-01', '2027-07-01', 'ACTIVE');

-- T_SVG_ENROLL_M: 자녀2가 정기적금 가입
INSERT INTO `T_SVG_ENROLL_M`
	(`id`, `saving_id`, `child_id`, `parent_id`, `wallet_id`, `applied_rate`, `applied_early_termination_rate`, `term_months`, `monthly_amount`, `payment_day`, `auto_transfer`, `start_date`, `maturity_date`, `status`)
VALUES (1, 1, 2, 1, 5, 4.2, 1.0, 12, 100000, 25, TRUE, '2026-07-25', '2027-07-25', 'ACTIVE');

-- T_LON_ENROLL_M: 자녀3가 대출 실행 (outstanding_principal = principal_amount로 시작)
INSERT INTO `T_LON_ENROLL_M`
	(`id`, `loan_id`, `parent_id`, `child_id`, `principal_amount`, `outstanding_principal`, `overdue_interest`, `applied_rate`, `applied_late_fee_rate`, `auto_transfer`, `payment_day`, `paid_count`, `term_months`, `start_date`, `maturity_date`, `status`)
VALUES (1, 1, 1, 3, 100000, 100000, 0, 5.5, 8.5, TRUE, 25, 0, 12, '2026-07-01', '2027-07-01', 'ACTIVE');

-- T_WLT_TRF_L: 적금 1회차 자동이체 성공 건
INSERT INTO `T_WLT_TRF_L` (`id`, `from_wallet_id`, `to_wallet_id`, `idempotency_key`, `amount`, `type`, `status`)
VALUES (1, 2, 5, UUID(), 100000, 'SAVING', 'COMPLETED');

-- T_SVG_PAYHIST_H: 위 transfer와 연결된 1회차 납입 완료
INSERT INTO `T_SVG_PAYHIST_H`
	(`id`, `saving_enrollment_id`, `transfer_id`, `installment_no`, `amount`, `paid_amount`, `status`, `paid_at`)
VALUES (1, 1, 1, 1, 100000, 100000, 'PAID', NOW());

-- T_SVG_PAYHIST_H: 2회차는 잔액부족으로 시도 자체가 없어 MISSED, transfer_id NULL
INSERT INTO `T_SVG_PAYHIST_H`
	(`id`, `saving_enrollment_id`, `transfer_id`, `installment_no`, `amount`, `paid_amount`, `status`)
VALUES (2, 1, NULL, 2, 100000, 0, 'MISSED');

-- T_WLT_TRF_L: 대출 1회차 상환 성공 건
INSERT INTO `T_WLT_TRF_L` (`id`, `from_wallet_id`, `to_wallet_id`, `idempotency_key`, `amount`, `type`, `status`)
VALUES (2, 3, 1, UUID(), 9000, 'LOAN', 'COMPLETED');

-- T_LON_REPAYHIST_H: 위 transfer와 연결된 1회차 상환 완료
INSERT INTO `T_LON_REPAYHIST_H`
	(`id`, `loan_enrollment_id`, `transfer_id`, `installment_no`, `principal_amount`, `paid_principal_amount`, `interest_amount`, `paid_interest_amount`, `status`, `paid_at`)
VALUES (1, 1, 2, 1, 8000, 8000, 1000, 1000, 'PAID', NOW());

-- T_PAY_TRAN_L: 자녀2 QR결제 성공 건 (편의점 카테고리)
INSERT INTO `T_PAY_TRAN_L` (`id`, `wallet_id`, `category_id`, `idempotency_key`, `applied_policy`, `amount`, `status`, `order_id`, `payment_key`)
VALUES (1, 2, 1, UUID(), 'ALLOW', 3500, 'SUCCESS', 'order-0001', 'toss-payment-key-0001');

-- T_WLT_CHARGE_L: 부모1이 카드로 자녀2 지갑에 충전
INSERT INTO `T_WLT_CHARGE_L` (`id`, `wallet_id`, `payment_method_id`, `idempotency_key`, `amount`, `status`, `order_id`, `payment_key`)
VALUES (1, 2, 1, UUID(), 50000, 'SUCCESS', 'charge-order-0001', 'toss-payment-key-charge-0001');

-- T_WLT_HIST_H: 결제/충전/송금 순서대로 반영 (지갑2 시작잔액 150,000원 기준)
-- 150,000 -3,500(결제)-> 146,500 +50,000(충전)-> 196,500 -100,000(적금이체)-> 96,500
INSERT INTO `T_WLT_HIST_H` (`id`, `wallet_id`, `payment_id`, `transfer_id`, `charge_id`, `direction`, `amount`, `balance_after`, `description`)
VALUES
(1, 2, 1, NULL, NULL, 'DEBIT', 3500, 146500, '편의점 결제'),
(2, 2, NULL, NULL, 1, 'CREDIT', 50000, 196500, '카드 충전'),
(3, 2, NULL, 1, NULL, 'DEBIT', 100000, 96500, '적금 1회차 자동이체');

-- T_QST_BASE_M: 부모1이 자녀2에게 퀘스트 생성
INSERT INTO `T_QST_BASE_M`
	(`id`, `parent_id`, `child_id`, `title`, `deadline`, `is_teeny_score`, `reward_amount`, `reward_teeny_score`, `status`)
VALUES (1, 1, 2, '방 청소하기', '2026-08-05 23:59:59', TRUE, 1000, 10, 'IN_PROGRESS');

-- T_QST_VERIFY_L: 자녀2가 인증 제출
INSERT INTO `T_QST_VERIFY_L` (`id`, `quest_id`, `image_url`, `content`, `status`)
VALUES (1, 1, 'https://cdn.test.com/quest/1.jpg', '깨끗하게 치웠어요!', 'PENDING');

-- T_TNY_SCOREHIST_H: 퀘스트 성공으로 점수 상승
INSERT INTO `T_TNY_SCOREHIST_H` (`id`, `child_id`, `amount`, `score_after`, `description`, `reference_type`, `reference_id`)
VALUES (1, 2, 10, 610, '퀘스트성공', 'QUEST', 1);

-- T_TDP_REQ_L + T_TDP_REQCTGR_R
INSERT INTO `T_TDP_REQ_L` (`id`, `parent_id`, `child_id`, `reason`, `status`, `expired_at`)
VALUES (1, 1, 3, '친구 생일선물 사려고요', 'PENDING', '2026-07-30 23:59:59');

INSERT INTO `T_TDP_REQCTGR_R` (`today_permission_id`, `merchant_category_id`)
VALUES (1, 1);

-- T_ALW_SCHEDULE_M: 부모1 -> 자녀3, 매달 5일 지급
INSERT INTO `T_ALW_SCHEDULE_M` (`id`, `parent_id`, `child_id`, `amount`, `cycle_type`, `payment_day`, `next_payment_date`)
VALUES (1, 1, 3, 20000, 'MONTHLY', 5, '2026-08-05');

-- T_NTF_NOTI_L: 결제 알림
INSERT INTO `T_NTF_NOTI_L` (`id`, `member_id`, `type`, `content`, `reference_type`, `reference_id`)
VALUES (1, 1, 'PAYMENT', '자녀2가 3,500원을 결제했어요', 'PAYMENT', 1);
