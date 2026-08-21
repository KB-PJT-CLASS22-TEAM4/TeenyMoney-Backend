-- Local-only demo data for the monthly money report.
-- Required execution order for a new local database:
--   1. schema/teenymoney_schema_renamed.sql
--   2. every migration through V020 in filename order (V006_1 included; V019 absent)
--   3. seed/01_seed_valid_data.sql
--   4. this file
-- Login password for every demo member: Local1234!
-- This script is append-only and refuses to run twice.

SET NAMES utf8mb4;
SET @month0 := DATE_FORMAT(CURDATE(), '%Y-%m-01');
SET @month1 := DATE_SUB(@month0, INTERVAL 1 MONTH);
SET @month2 := DATE_SUB(@month0, INTERVAL 2 MONTH);

DROP TEMPORARY TABLE IF EXISTS money_report_seed_guard;
CREATE TEMPORARY TABLE money_report_seed_guard (
    ok TINYINT NOT NULL,
    CONSTRAINT CK_MONEY_REPORT_SEED_GUARD CHECK (ok = 1)
);
INSERT INTO money_report_seed_guard (ok)
SELECT IF(COUNT(*) = 0, 1, 0)
FROM T_MBR_INFO_M
WHERE email IN (
    'report-parent@naver.com',
    'report-junior@gmail.com',
    'report-teen@gmail.com',
    'report-empty@gmail.com'
);
DROP TEMPORARY TABLE money_report_seed_guard;

START TRANSACTION;

-- ---------------------------------------------------------------------
-- Members, agreements, guardians, connection and wallets
-- ---------------------------------------------------------------------
SET @seed_password := '$2a$10$Ii6qH9kVC2z.mkEdiVas9.dN9yr/wZXPoSUgExNjp7N9Dra8avcSy';

-- profile_image_key 는 PARENT 에만 명시한다. 컬럼 DEFAULT 는 role 을 볼 수 없어
-- 자녀 이미지로 고정돼 있다(V030). 자녀 행은 DEFAULT 그대로가 맞다.
INSERT INTO T_MBR_INFO_M
    (`role`, name, birth_date, phone_number, email, password,
     teeny_score, customer_key, profile_image_key, status, created_at)
VALUES
    ('PARENT', '리포트부모', DATE_SUB(CURDATE(), INTERVAL 40 YEAR),
     '01090000001', 'report-parent@naver.com', @seed_password,
     NULL, 'report-demo-parent', 'teenymoney_parent.png', 'ACTIVE', @month2);
SET @parent_id := LAST_INSERT_ID();

INSERT INTO T_MBR_INFO_M
    (`role`, name, birth_date, phone_number, email, password,
     payment_password, teeny_score, applied_grade_id, grade_applied_at,
     status, created_at)
VALUES
    ('CHILD', '리포트주니어', DATE_SUB(CURDATE(), INTERVAL 10 YEAR),
     '01090000002', 'report-junior@gmail.com', @seed_password,
     @seed_password, 618,
     (SELECT grade_id FROM T_TNY_GRADE_A WHERE 600 BETWEEN min_score AND max_score),
     @month0, 'ACTIVE', @month2);
SET @junior_id := LAST_INSERT_ID();

INSERT INTO T_MBR_INFO_M
    (`role`, name, birth_date, phone_number, email, password,
     payment_password, teeny_score, applied_grade_id, grade_applied_at,
     status, created_at)
VALUES
    ('CHILD', '리포트틴', DATE_SUB(CURDATE(), INTERVAL 16 YEAR),
     '01090000003', 'report-teen@gmail.com', @seed_password,
     @seed_password, 697,
     (SELECT grade_id FROM T_TNY_GRADE_A WHERE 700 BETWEEN min_score AND max_score),
     @month0, 'ACTIVE', @month2);
SET @teen_id := LAST_INSERT_ID();

INSERT INTO T_MBR_INFO_M
    (`role`, name, birth_date, phone_number, email, password,
     payment_password, teeny_score, applied_grade_id, grade_applied_at,
     status, created_at)
VALUES
    ('CHILD', '리포트빈화면', DATE_SUB(CURDATE(), INTERVAL 9 YEAR),
     '01090000004', 'report-empty@gmail.com', @seed_password,
     @seed_password, 600,
     (SELECT grade_id FROM T_TNY_GRADE_A WHERE 600 BETWEEN min_score AND max_score),
     @month0, 'ACTIVE', @month0);
SET @empty_id := LAST_INSERT_ID();

INSERT INTO T_MBR_LEGAL_GUARDIAN_M
    (child_member_id, name, phone_number, relationship,
     verification_method, verification_reference, verified_at)
VALUES
    (@junior_id, '리포트부모', '01090000001', 'FATHER',
     'SMS_TEST', CONCAT('REPORT-GUARDIAN-', @junior_id), @month2),
    (@empty_id, '리포트부모', '01090000001', 'FATHER',
     'SMS_TEST', CONCAT('REPORT-GUARDIAN-', @empty_id), @month0);

INSERT INTO T_MBR_AGRMT_H
    (member_id, agreement_id, status, actor_type, actor_member_id,
     verification_method, verification_reference, created_at)
SELECT member.id,
       agreement.id,
       'AGREED',
       IF(member.id IN (@junior_id, @empty_id), 'LEGAL_GUARDIAN', 'SELF'),
       IF(member.id IN (@junior_id, @empty_id), @parent_id, member.id),
       IF(member.id IN (@junior_id, @empty_id), 'SMS', NULL),
       IF(member.id IN (@junior_id, @empty_id),
          CONCAT('REPORT-CONSENT-', member.id, '-', agreement.id), NULL),
       member.created_at
FROM T_MBR_INFO_M member
CROSS JOIN T_MBR_AGRMT_M agreement
WHERE member.id IN (@parent_id, @junior_id, @teen_id, @empty_id)
  AND agreement.is_required = TRUE;

INSERT INTO T_MBR_CONN_R (parent_id, child_id, status, created_at)
VALUES
    (@parent_id, @junior_id, 'ACTIVE', @month2),
    (@parent_id, @teen_id, 'ACTIVE', @month2),
    (@parent_id, @empty_id, 'ACTIVE', @month0);

INSERT INTO T_WLT_BASE_M (member_id, balance, type, created_at)
VALUES (@parent_id, 1010000, 'MEMBER', @month2);
SET @parent_wallet := LAST_INSERT_ID();
INSERT INTO T_WLT_BASE_M (member_id, balance, type, created_at)
VALUES (@junior_id, 87000, 'MEMBER', @month2);
SET @junior_wallet := LAST_INSERT_ID();
INSERT INTO T_WLT_BASE_M (member_id, balance, type, created_at)
VALUES (@teen_id, 43000, 'MEMBER', @month2);
SET @teen_wallet := LAST_INSERT_ID();
INSERT INTO T_WLT_BASE_M (member_id, balance, type, created_at)
VALUES (@empty_id, 0, 'MEMBER', @month0);
SET @empty_wallet := LAST_INSERT_ID();
INSERT INTO T_WLT_BASE_M (member_id, balance, type, created_at)
VALUES (@junior_id, 30000, 'SAVING', @month2);
SET @junior_saving_wallet := LAST_INSERT_ID();
INSERT INTO T_WLT_BASE_M (member_id, balance, type, created_at)
VALUES (@teen_id, 50000, 'DEPOSIT', @month1);
SET @teen_deposit_wallet := LAST_INSERT_ID();

-- ---------------------------------------------------------------------
-- Product enrollments
-- ---------------------------------------------------------------------
SET @saving_product := (SELECT id FROM T_SVG_PROD_M WHERE name = '티니 정기적금' LIMIT 1);
SET @loan_product := (SELECT id FROM T_LON_PROD_M WHERE name = '플러스 원리금균등 대출' LIMIT 1);

INSERT INTO T_DPT_PROD_M
    (fin_co_no, fin_prdt_cd, kor_co_nm, name, interest_calculation_type,
     rate_3m, early_termination_rate, min_amount, max_amount,
     description, is_active)
VALUES
    ('REPORT001', 'REPORT-DEPOSIT-3M', '리포트은행',
     '리포트 데모 금융기관 예금', 'SIMPLE',
     3.20, 0.50, 10000, 1000000,
     '머니 리포트 로컬 시연용 금융기관 예금', TRUE);
SET @deposit_product := LAST_INSERT_ID();

INSERT INTO T_SVG_ENROLL_M
    (saving_id, child_id, parent_id, wallet_id,
     applied_rate, applied_early_termination_rate, term_months,
     monthly_amount, payment_day, auto_transfer,
     start_date, maturity_date, status, created_at)
VALUES
    (@saving_product, @junior_id, @parent_id, @junior_saving_wallet,
     4.20, 1.00, 6, 10000, 5, TRUE,
     DATE_ADD(@month2, INTERVAL 4 DAY),
     DATE_ADD(@month2, INTERVAL 6 MONTH),
     'ACTIVE', DATE_ADD(@month2, INTERVAL 1 DAY));
SET @junior_saving := LAST_INSERT_ID();

INSERT INTO T_DPT_ENROLL_M
    (deposit_id, child_id, parent_id, wallet_id,
     applied_rate, applied_early_termination_rate, term_months,
     start_date, maturity_date, status, created_at)
VALUES
    (@deposit_product, @teen_id, @parent_id, @teen_deposit_wallet,
     3.20, 0.50, 3,
     DATE_ADD(@month1, INTERVAL 2 DAY),
     DATE_ADD(@month1, INTERVAL 3 MONTH),
     'ACTIVE', DATE_ADD(@month1, INTERVAL 1 DAY));
SET @teen_deposit := LAST_INSERT_ID();

INSERT INTO T_LON_ENROLL_M
    (loan_id, parent_id, child_id, principal_amount,
     outstanding_principal, overdue_interest,
     applied_rate, applied_late_fee_rate, auto_transfer,
     payment_day, paid_count, term_months,
     start_date, maturity_date, status, created_at)
VALUES
    (@loan_product, @parent_id, @teen_id, 60000,
     40000, 500, 5.00, 8.00, TRUE,
     8, 2, 6,
     DATE_ADD(@month2, INTERVAL 2 DAY),
     DATE_ADD(@month2, INTERVAL 6 MONTH),
     'OVERDUE', DATE_ADD(@month2, INTERVAL 1 DAY));
SET @teen_loan := LAST_INSERT_ID();

-- ---------------------------------------------------------------------
-- Transfers and their two-sided wallet ledger entries
-- ---------------------------------------------------------------------
-- Junior saving installment 1: 200,000 -> 190,000; saving 0 -> 10,000
INSERT INTO T_WLT_TRF_L
    (from_wallet_id, to_wallet_id, idempotency_key, amount, type, status, created_at)
VALUES (@junior_wallet, @junior_saving_wallet, UUID(), 10000, 'SAVING', 'COMPLETED',
        TIMESTAMP(DATE_ADD(@month2, INTERVAL 4 DAY), '09:00:00'));
SET @junior_saving_transfer1 := LAST_INSERT_ID();
INSERT INTO T_WLT_HIST_H
    (wallet_id, transfer_id, direction, amount, balance_after, description, created_at)
VALUES
    (@junior_wallet, @junior_saving_transfer1, 'DEBIT', 10000, 190000,
     '리포트 적금 1회차 납입', TIMESTAMP(DATE_ADD(@month2, INTERVAL 4 DAY), '09:00:00')),
    (@junior_saving_wallet, @junior_saving_transfer1, 'CREDIT', 10000, 10000,
     '리포트 적금 1회차 입금', TIMESTAMP(DATE_ADD(@month2, INTERVAL 4 DAY), '09:00:00'));
INSERT INTO T_SVG_PAYHIST_H
    (saving_enrollment_id, transfer_id, installment_no, amount,
     paid_amount, status, created_at, paid_at)
VALUES (@junior_saving, @junior_saving_transfer1, 1, 10000,
        10000, 'PAID', TIMESTAMP(DATE_ADD(@month2, INTERVAL 4 DAY), '09:00:00'),
        TIMESTAMP(DATE_ADD(@month2, INTERVAL 4 DAY), '09:00:00'));

-- Teen loan repayment 1: 210,000 -> 199,000; parent 1,000,000 -> 1,011,000
INSERT INTO T_WLT_TRF_L
    (from_wallet_id, to_wallet_id, idempotency_key, amount, type, status, created_at)
VALUES (@teen_wallet, @parent_wallet, UUID(), 11000, 'LOAN', 'COMPLETED',
        TIMESTAMP(DATE_ADD(@month2, INTERVAL 7 DAY), '09:00:00'));
SET @teen_loan_transfer1 := LAST_INSERT_ID();
INSERT INTO T_WLT_HIST_H
    (wallet_id, transfer_id, direction, amount, balance_after, description, created_at)
VALUES
    (@teen_wallet, @teen_loan_transfer1, 'DEBIT', 11000, 199000,
     '리포트 대출 1회차 상환', TIMESTAMP(DATE_ADD(@month2, INTERVAL 7 DAY), '09:00:00')),
    (@parent_wallet, @teen_loan_transfer1, 'CREDIT', 11000, 1011000,
     '리포트 대출 1회차 상환 수취', TIMESTAMP(DATE_ADD(@month2, INTERVAL 7 DAY), '09:00:00'));
INSERT INTO T_LON_REPAYHIST_H
    (loan_enrollment_id, transfer_id, installment_no,
     principal_amount, paid_principal_amount,
     interest_amount, paid_interest_amount, status, created_at, paid_at)
VALUES (@teen_loan, @teen_loan_transfer1, 1,
        10000, 10000, 1000, 1000, 'PAID',
        TIMESTAMP(DATE_ADD(@month2, INTERVAL 7 DAY), '09:00:00'),
        TIMESTAMP(DATE_ADD(@month2, INTERVAL 7 DAY), '09:00:00'));

-- Junior saving installment 2: 160,000 -> 150,000; saving 10,000 -> 20,000
INSERT INTO T_WLT_TRF_L
    (from_wallet_id, to_wallet_id, idempotency_key, amount, type, status, created_at)
VALUES (@junior_wallet, @junior_saving_wallet, UUID(), 10000, 'SAVING', 'COMPLETED',
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 4 DAY), '09:00:00'));
SET @junior_saving_transfer2 := LAST_INSERT_ID();
INSERT INTO T_WLT_HIST_H
    (wallet_id, transfer_id, direction, amount, balance_after, description, created_at)
VALUES
    (@junior_wallet, @junior_saving_transfer2, 'DEBIT', 10000, 150000,
     '리포트 적금 2회차 납입', TIMESTAMP(DATE_ADD(@month1, INTERVAL 4 DAY), '09:00:00')),
    (@junior_saving_wallet, @junior_saving_transfer2, 'CREDIT', 10000, 20000,
     '리포트 적금 2회차 입금', TIMESTAMP(DATE_ADD(@month1, INTERVAL 4 DAY), '09:00:00'));
INSERT INTO T_SVG_PAYHIST_H
    (saving_enrollment_id, transfer_id, installment_no, amount,
     paid_amount, status, created_at, paid_at)
VALUES (@junior_saving, @junior_saving_transfer2, 2, 10000,
        10000, 'PAID', TIMESTAMP(DATE_ADD(@month1, INTERVAL 4 DAY), '09:00:00'),
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 4 DAY), '09:00:00'));

-- Teen deposit: 97,000 -> 47,000; deposit 0 -> 50,000
INSERT INTO T_WLT_TRF_L
    (from_wallet_id, to_wallet_id, idempotency_key, amount, type, status, created_at)
VALUES (@teen_wallet, @teen_deposit_wallet, UUID(), 50000, 'DEPOSIT', 'COMPLETED',
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 2 DAY), '10:00:00'));
SET @teen_deposit_transfer := LAST_INSERT_ID();
INSERT INTO T_WLT_HIST_H
    (wallet_id, transfer_id, direction, amount, balance_after, description, created_at)
VALUES
    (@teen_wallet, @teen_deposit_transfer, 'DEBIT', 50000, 47000,
     '리포트 금융기관 예금 예치', TIMESTAMP(DATE_ADD(@month1, INTERVAL 2 DAY), '10:00:00')),
    (@teen_deposit_wallet, @teen_deposit_transfer, 'CREDIT', 50000, 50000,
     '리포트 금융기관 예금 입금', TIMESTAMP(DATE_ADD(@month1, INTERVAL 2 DAY), '10:00:00'));

-- Teen loan repayment 2: 47,000 -> 36,000; parent 1,011,000 -> 1,022,000
INSERT INTO T_WLT_TRF_L
    (from_wallet_id, to_wallet_id, idempotency_key, amount, type, status, created_at)
VALUES (@teen_wallet, @parent_wallet, UUID(), 11000, 'LOAN', 'COMPLETED',
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 7 DAY), '09:00:00'));
SET @teen_loan_transfer2 := LAST_INSERT_ID();
INSERT INTO T_WLT_HIST_H
    (wallet_id, transfer_id, direction, amount, balance_after, description, created_at)
VALUES
    (@teen_wallet, @teen_loan_transfer2, 'DEBIT', 11000, 36000,
     '리포트 대출 2회차 상환', TIMESTAMP(DATE_ADD(@month1, INTERVAL 7 DAY), '09:00:00')),
    (@parent_wallet, @teen_loan_transfer2, 'CREDIT', 11000, 1022000,
     '리포트 대출 2회차 상환 수취', TIMESTAMP(DATE_ADD(@month1, INTERVAL 7 DAY), '09:00:00'));
INSERT INTO T_LON_REPAYHIST_H
    (loan_enrollment_id, transfer_id, installment_no,
     principal_amount, paid_principal_amount,
     interest_amount, paid_interest_amount, status, created_at, paid_at)
VALUES (@teen_loan, @teen_loan_transfer2, 2,
        10000, 10000, 1000, 1000, 'PAID',
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 7 DAY), '09:00:00'),
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 7 DAY), '09:00:00'));

-- Junior saving installment 3: 118,000 -> 108,000; saving 20,000 -> 30,000
INSERT INTO T_WLT_TRF_L
    (from_wallet_id, to_wallet_id, idempotency_key, amount, type, status, created_at)
VALUES (@junior_wallet, @junior_saving_wallet, UUID(), 10000, 'SAVING', 'COMPLETED',
        TIMESTAMP(DATE_ADD(@month0, INTERVAL 4 DAY), '09:00:00'));
SET @junior_saving_transfer3 := LAST_INSERT_ID();
INSERT INTO T_WLT_HIST_H
    (wallet_id, transfer_id, direction, amount, balance_after, description, created_at)
VALUES
    (@junior_wallet, @junior_saving_transfer3, 'DEBIT', 10000, 108000,
     '리포트 적금 3회차 납입', TIMESTAMP(DATE_ADD(@month0, INTERVAL 4 DAY), '09:00:00')),
    (@junior_saving_wallet, @junior_saving_transfer3, 'CREDIT', 10000, 30000,
     '리포트 적금 3회차 입금', TIMESTAMP(DATE_ADD(@month0, INTERVAL 4 DAY), '09:00:00'));
INSERT INTO T_SVG_PAYHIST_H
    (saving_enrollment_id, transfer_id, installment_no, amount,
     paid_amount, status, created_at, paid_at)
VALUES (@junior_saving, @junior_saving_transfer3, 3, 10000,
        10000, 'PAID', TIMESTAMP(DATE_ADD(@month0, INTERVAL 4 DAY), '09:00:00'),
        TIMESTAMP(DATE_ADD(@month0, INTERVAL 4 DAY), '09:00:00'));

-- Current overdue loan installment (no transfer because nothing was paid).
INSERT INTO T_LON_REPAYHIST_H
    (loan_enrollment_id, transfer_id, installment_no,
     principal_amount, paid_principal_amount,
     interest_amount, paid_interest_amount, status, created_at, overdue_start_at)
VALUES (@teen_loan, NULL, 3, 10000, 0, 1000, 0, 'OVERDUE',
        TIMESTAMP(DATE_ADD(@month0, INTERVAL 7 DAY), '09:00:00'),
        TIMESTAMP(DATE_ADD(@month0, INTERVAL 7 DAY), '09:00:00'));

-- ---------------------------------------------------------------------
-- Successful QR payments and matching debit ledger entries
-- ---------------------------------------------------------------------
SET @cat_store := (SELECT id FROM T_MCC_CTGR_C WHERE name = '편의점');
SET @cat_cafe := (SELECT id FROM T_MCC_CTGR_C WHERE name = '카페·디저트');
SET @cat_books := (SELECT id FROM T_MCC_CTGR_C WHERE name = '문구·도서·완구');
SET @cat_game := (SELECT id FROM T_MCC_CTGR_C WHERE name = '게임');
SET @cat_transport := (SELECT id FROM T_MCC_CTGR_C WHERE name = '대중교통');
SET @cat_shopping := (SELECT id FROM T_MCC_CTGR_C WHERE name = '온라인쇼핑');

-- junior: two months ago 30,000 / 3
INSERT INTO T_PAY_TRAN_L
    (wallet_id, category_id, idempotency_key, applied_policy, amount,
     status, order_id, payment_key, created_at)
VALUES (@junior_wallet, @cat_store, UUID(), 'ALLOW', 12000, 'SUCCESS',
        CONCAT('report-j-p-', UUID()), CONCAT('report-pay-', UUID()),
        TIMESTAMP(DATE_ADD(@month2, INTERVAL 9 DAY), '17:00:00'));
SET @payment := LAST_INSERT_ID();
INSERT INTO T_WLT_HIST_H
    (wallet_id, payment_id, direction, amount, balance_after, description, created_at)
VALUES (@junior_wallet, @payment, 'DEBIT', 12000, 178000, '리포트 편의점 결제',
        TIMESTAMP(DATE_ADD(@month2, INTERVAL 9 DAY), '17:00:00'));
INSERT INTO T_PAY_TRAN_L
    (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@junior_wallet, @cat_cafe, UUID(), 'ALLOW', 8000, 'SUCCESS', CONCAT('report-j-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month2, INTERVAL 15 DAY), '16:00:00'));
SET @payment := LAST_INSERT_ID();
INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at)
VALUES (@junior_wallet, @payment, 'DEBIT', 8000, 170000, '리포트 카페 결제', TIMESTAMP(DATE_ADD(@month2, INTERVAL 15 DAY), '16:00:00'));
INSERT INTO T_PAY_TRAN_L
    (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@junior_wallet, @cat_books, UUID(), 'ALLOW', 10000, 'SUCCESS', CONCAT('report-j-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month2, INTERVAL 21 DAY), '15:00:00'));
SET @payment := LAST_INSERT_ID();
INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at)
VALUES (@junior_wallet, @payment, 'DEBIT', 10000, 160000, '리포트 문구 결제', TIMESTAMP(DATE_ADD(@month2, INTERVAL 21 DAY), '15:00:00'));

-- junior: previous month 35,000 / 4
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@junior_wallet, @cat_store, UUID(), 'ALLOW', 8000, 'SUCCESS', CONCAT('report-j-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month1, INTERVAL 7 DAY), '17:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@junior_wallet, @payment, 'DEBIT', 8000, 142000, '리포트 편의점 결제', TIMESTAMP(DATE_ADD(@month1, INTERVAL 7 DAY), '17:00:00'));
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@junior_wallet, @cat_cafe, UUID(), 'ALLOW', 9000, 'SUCCESS', CONCAT('report-j-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month1, INTERVAL 12 DAY), '16:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@junior_wallet, @payment, 'DEBIT', 9000, 133000, '리포트 카페 결제', TIMESTAMP(DATE_ADD(@month1, INTERVAL 12 DAY), '16:00:00'));
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@junior_wallet, @cat_books, UUID(), 'ALLOW', 10000, 'SUCCESS', CONCAT('report-j-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month1, INTERVAL 16 DAY), '15:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@junior_wallet, @payment, 'DEBIT', 10000, 123000, '리포트 문구 결제', TIMESTAMP(DATE_ADD(@month1, INTERVAL 16 DAY), '15:00:00'));
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@junior_wallet, @cat_store, UUID(), 'ALLOW', 8000, 'SUCCESS', CONCAT('report-j-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month1, INTERVAL 18 DAY), '18:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@junior_wallet, @payment, 'DEBIT', 8000, 115000, '리포트 편의점 결제', TIMESTAMP(DATE_ADD(@month1, INTERVAL 18 DAY), '18:00:00'));

-- junior: current month 26,000 / 3
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@junior_wallet, @cat_store, UUID(), 'ALLOW', 7000, 'SUCCESS', CONCAT('report-j-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month0, INTERVAL 5 DAY), '17:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@junior_wallet, @payment, 'DEBIT', 7000, 101000, '리포트 편의점 결제', TIMESTAMP(DATE_ADD(@month0, INTERVAL 5 DAY), '17:00:00'));
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@junior_wallet, @cat_cafe, UUID(), 'ALLOW', 8000, 'SUCCESS', CONCAT('report-j-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month0, INTERVAL 7 DAY), '16:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@junior_wallet, @payment, 'DEBIT', 8000, 93000, '리포트 카페 결제', TIMESTAMP(DATE_ADD(@month0, INTERVAL 7 DAY), '16:00:00'));
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@junior_wallet, @cat_books, UUID(), 'ALLOW', 11000, 'SUCCESS', CONCAT('report-j-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month0, INTERVAL 9 DAY), '15:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@junior_wallet, @payment, 'DEBIT', 11000, 82000, '리포트 문구 결제', TIMESTAMP(DATE_ADD(@month0, INTERVAL 9 DAY), '15:00:00'));

-- teen: two months ago 40,000 / 3
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@teen_wallet, @cat_game, UUID(), 'WATCH', 15000, 'SUCCESS', CONCAT('report-t-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month2, INTERVAL 5 DAY), '20:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@teen_wallet, @payment, 'DEBIT', 15000, 235000, '리포트 게임 결제', TIMESTAMP(DATE_ADD(@month2, INTERVAL 5 DAY), '20:00:00'));
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@teen_wallet, @cat_cafe, UUID(), 'ALLOW', 10000, 'SUCCESS', CONCAT('report-t-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month2, INTERVAL 6 DAY), '17:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@teen_wallet, @payment, 'DEBIT', 10000, 225000, '리포트 카페 결제', TIMESTAMP(DATE_ADD(@month2, INTERVAL 6 DAY), '17:00:00'));
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@teen_wallet, @cat_transport, UUID(), 'ALLOW', 15000, 'SUCCESS', CONCAT('report-t-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month2, INTERVAL 7 DAY), '08:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@teen_wallet, @payment, 'DEBIT', 15000, 210000, '리포트 교통 결제', TIMESTAMP(DATE_ADD(@month2, INTERVAL 7 DAY), '08:00:00'));

-- teen: previous month 52,000 / 4
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@teen_wallet, @cat_game, UUID(), 'WATCH', 12000, 'SUCCESS', CONCAT('report-t-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month1, INTERVAL 4 DAY), '20:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@teen_wallet, @payment, 'DEBIT', 12000, 187000, '리포트 게임 결제', TIMESTAMP(DATE_ADD(@month1, INTERVAL 4 DAY), '20:00:00'));
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@teen_wallet, @cat_shopping, UUID(), 'WATCH', 15000, 'SUCCESS', CONCAT('report-t-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month1, INTERVAL 10 DAY), '19:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@teen_wallet, @payment, 'DEBIT', 15000, 172000, '리포트 온라인쇼핑 결제', TIMESTAMP(DATE_ADD(@month1, INTERVAL 10 DAY), '19:00:00'));
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@teen_wallet, @cat_cafe, UUID(), 'ALLOW', 10000, 'SUCCESS', CONCAT('report-t-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month1, INTERVAL 15 DAY), '17:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@teen_wallet, @payment, 'DEBIT', 10000, 162000, '리포트 카페 결제', TIMESTAMP(DATE_ADD(@month1, INTERVAL 15 DAY), '17:00:00'));
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@teen_wallet, @cat_transport, UUID(), 'ALLOW', 15000, 'SUCCESS', CONCAT('report-t-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month1, INTERVAL 20 DAY), '08:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@teen_wallet, @payment, 'DEBIT', 15000, 147000, '리포트 교통 결제', TIMESTAMP(DATE_ADD(@month1, INTERVAL 20 DAY), '08:00:00'));

-- teen: current month 47,000 / 3
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@teen_wallet, @cat_game, UUID(), 'WATCH', 12000, 'SUCCESS', CONCAT('report-t-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month0, INTERVAL 3 DAY), '20:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@teen_wallet, @payment, 'DEBIT', 12000, 78000, '리포트 게임 결제', TIMESTAMP(DATE_ADD(@month0, INTERVAL 3 DAY), '20:00:00'));
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@teen_wallet, @cat_shopping, UUID(), 'WATCH', 20000, 'SUCCESS', CONCAT('report-t-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month0, INTERVAL 6 DAY), '19:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@teen_wallet, @payment, 'DEBIT', 20000, 58000, '리포트 온라인쇼핑 결제', TIMESTAMP(DATE_ADD(@month0, INTERVAL 6 DAY), '19:00:00'));
INSERT INTO T_PAY_TRAN_L (wallet_id, category_id, idempotency_key, applied_policy, amount, status, order_id, payment_key, created_at)
VALUES (@teen_wallet, @cat_transport, UUID(), 'ALLOW', 15000, 'SUCCESS', CONCAT('report-t-p-', UUID()), CONCAT('report-pay-', UUID()), TIMESTAMP(DATE_ADD(@month0, INTERVAL 9 DAY), '08:00:00'));
SET @payment := LAST_INSERT_ID(); INSERT INTO T_WLT_HIST_H (wallet_id, payment_id, direction, amount, balance_after, description, created_at) VALUES (@teen_wallet, @payment, 'DEBIT', 15000, 43000, '리포트 교통 결제', TIMESTAMP(DATE_ADD(@month0, INTERVAL 9 DAY), '08:00:00'));

-- V018: 결제 시점 가맹점명 스냅샷. 위에서 생성한 리포트 결제 행만 갱신한다.
UPDATE T_PAY_TRAN_L
SET merchant_name = CASE category_id
    WHEN @cat_store THEN '리포트 편의점'
    WHEN @cat_cafe THEN '리포트 카페'
    WHEN @cat_books THEN '리포트 문구점'
    WHEN @cat_game THEN '리포트 게임샵'
    WHEN @cat_transport THEN '리포트 교통'
    WHEN @cat_shopping THEN '리포트 온라인몰'
END
WHERE wallet_id IN (@junior_wallet, @teen_wallet)
  AND merchant_name IS NULL;

-- ---------------------------------------------------------------------
-- Today-only permission requests
-- ---------------------------------------------------------------------
INSERT INTO T_TDP_REQ_L
    (parent_id, child_id, reason, status, expired_at, created_at, reviewed_at)
VALUES (@parent_id, @junior_id, '친구와 영화를 보려고 해요', 'APPROVED',
        TIMESTAMP(LAST_DAY(@month1), '23:59:59'),
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 11 DAY), '10:00:00'),
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 11 DAY), '10:10:00'));
SET @permission := LAST_INSERT_ID();
INSERT INTO T_TDP_REQCTGR_R (today_permission_id, merchant_category_id, created_at)
VALUES (@permission, (SELECT id FROM T_MCC_CTGR_C WHERE name = '영화·공연·테마파크'), TIMESTAMP(DATE_ADD(@month1, INTERVAL 11 DAY), '10:00:00'));

INSERT INTO T_TDP_REQ_L
    (parent_id, child_id, reason, status, expired_at, created_at, reviewed_at)
VALUES (@parent_id, @junior_id, '게임 아이템을 사고 싶어요', 'REJECTED',
        TIMESTAMP(LAST_DAY(@month0), '23:59:59'),
        TIMESTAMP(DATE_ADD(@month0, INTERVAL 6 DAY), '10:00:00'),
        TIMESTAMP(DATE_ADD(@month0, INTERVAL 6 DAY), '10:15:00'));
SET @permission := LAST_INSERT_ID();
INSERT INTO T_TDP_REQCTGR_R (today_permission_id, merchant_category_id, created_at)
VALUES (@permission, @cat_game, TIMESTAMP(DATE_ADD(@month0, INTERVAL 6 DAY), '10:00:00'));

INSERT INTO T_TDP_REQ_L
    (parent_id, child_id, reason, status, expired_at, created_at, reviewed_at)
VALUES (@parent_id, @teen_id, '필요한 물건을 온라인으로 주문하려고 해요', 'APPROVED',
        TIMESTAMP(LAST_DAY(@month1), '23:59:59'),
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 9 DAY), '10:00:00'),
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 9 DAY), '10:05:00'));
SET @permission := LAST_INSERT_ID();
INSERT INTO T_TDP_REQCTGR_R (today_permission_id, merchant_category_id, created_at)
VALUES (@permission, @cat_shopping, TIMESTAMP(DATE_ADD(@month1, INTERVAL 9 DAY), '10:00:00'));

INSERT INTO T_TDP_REQ_L
    (parent_id, child_id, reason, status, expired_at, created_at)
VALUES (@parent_id, @teen_id, '게임 이용 시간을 늘리고 싶어요', 'EXPIRED',
        TIMESTAMP(DATE_ADD(@month0, INTERVAL 4 DAY), '23:59:59'),
        TIMESTAMP(DATE_ADD(@month0, INTERVAL 4 DAY), '10:00:00'));
SET @permission := LAST_INSERT_ID();
INSERT INTO T_TDP_REQCTGR_R (today_permission_id, merchant_category_id, created_at)
VALUES (@permission, @cat_game, TIMESTAMP(DATE_ADD(@month0, INTERVAL 4 DAY), '10:00:00'));

-- ---------------------------------------------------------------------
-- Quests, reward transfers and score history
-- ---------------------------------------------------------------------
INSERT INTO T_QST_BASE_M
    (parent_id, child_id, creation_request_key, title, content, deadline,
     is_teeny_score, verification_requirement, reward_amount,
     status, remaining_count, ended_at, created_at)
VALUES
    (@parent_id, @junior_id, UUID(), '책상 정리하기', '책상과 책장을 정리해 주세요.',
     TIMESTAMP(DATE_ADD(@month1, INTERVAL 19 DAY), '20:00:00'), TRUE, 'PHOTO_REQUIRED',
     3000, 'COMPLETED', 3, TIMESTAMP(DATE_ADD(@month1, INTERVAL 18 DAY), '18:00:00'),
     TIMESTAMP(DATE_ADD(@month1, INTERVAL 12 DAY), '09:00:00'));
SET @junior_quest1 := LAST_INSERT_ID();
INSERT INTO T_QST_VERIFY_L
    (quest_id, attempt_no, image_key, content, status, created_at, reviewed_at)
VALUES (@junior_quest1, 1, CONCAT('report-demo/', @junior_quest1, '.jpg'),
        '정리를 완료했어요.', 'APPROVED',
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 18 DAY), '17:00:00'),
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 18 DAY), '18:00:00'));

INSERT INTO T_WLT_TRF_L
    (from_wallet_id, to_wallet_id, idempotency_key, amount, type, status, created_at)
VALUES (@parent_wallet, @junior_wallet, UUID(), 3000, 'QUEST_REWARD', 'COMPLETED',
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 18 DAY), '18:00:00'));
SET @junior_reward1 := LAST_INSERT_ID();
INSERT INTO T_WLT_HIST_H
    (wallet_id, transfer_id, direction, amount, balance_after, description, created_at)
VALUES
    (@parent_wallet, @junior_reward1, 'DEBIT', 3000, 1019000, '리포트 퀘스트 보상 지급', TIMESTAMP(DATE_ADD(@month1, INTERVAL 18 DAY), '18:00:00')),
    (@junior_wallet, @junior_reward1, 'CREDIT', 3000, 118000, '리포트 퀘스트 보상', TIMESTAMP(DATE_ADD(@month1, INTERVAL 18 DAY), '18:00:00'));

INSERT INTO T_QST_BASE_M
    (parent_id, child_id, creation_request_key, title, content, deadline,
     is_teeny_score, verification_requirement, reward_amount,
     status, remaining_count, ended_at, created_at)
VALUES
    (@parent_id, @junior_id, UUID(), '분리수거 돕기', '재활용품 분리수거를 도와주세요.',
     TIMESTAMP(DATE_ADD(@month0, INTERVAL 10 DAY), '20:00:00'), TRUE, 'TEXT_REQUIRED',
     5000, 'COMPLETED', 3, TIMESTAMP(DATE_ADD(@month0, INTERVAL 10 DAY), '18:00:00'),
     TIMESTAMP(DATE_ADD(@month0, INTERVAL 5 DAY), '09:00:00'));
SET @junior_quest2 := LAST_INSERT_ID();
INSERT INTO T_QST_VERIFY_L
    (quest_id, attempt_no, content, status, created_at, reviewed_at)
VALUES (@junior_quest2, 1, '분리수거를 마쳤어요.', 'APPROVED',
        TIMESTAMP(DATE_ADD(@month0, INTERVAL 10 DAY), '17:00:00'),
        TIMESTAMP(DATE_ADD(@month0, INTERVAL 10 DAY), '18:00:00'));
INSERT INTO T_WLT_TRF_L
    (from_wallet_id, to_wallet_id, idempotency_key, amount, type, status, created_at)
VALUES (@parent_wallet, @junior_wallet, UUID(), 5000, 'QUEST_REWARD', 'COMPLETED',
        TIMESTAMP(DATE_ADD(@month0, INTERVAL 10 DAY), '18:00:00'));
SET @junior_reward2 := LAST_INSERT_ID();
INSERT INTO T_WLT_HIST_H
    (wallet_id, transfer_id, direction, amount, balance_after, description, created_at)
VALUES
    (@parent_wallet, @junior_reward2, 'DEBIT', 5000, 1010000, '리포트 퀘스트 보상 지급', TIMESTAMP(DATE_ADD(@month0, INTERVAL 10 DAY), '18:00:00')),
    (@junior_wallet, @junior_reward2, 'CREDIT', 5000, 87000, '리포트 퀘스트 보상', TIMESTAMP(DATE_ADD(@month0, INTERVAL 10 DAY), '18:00:00'));

INSERT INTO T_QST_BASE_M
    (parent_id, child_id, creation_request_key, title, content, deadline,
     is_teeny_score, verification_requirement, reward_amount,
     status, remaining_count, created_at)
VALUES
    (@parent_id, @junior_id, UUID(), '화분 물주기', '거실 화분에 물을 주세요.',
     TIMESTAMP(LAST_DAY(@month0), '20:00:00'), FALSE, 'FREE',
     1000, 'IN_PROGRESS', 3, TIMESTAMP(DATE_ADD(@month0, INTERVAL 11 DAY), '09:00:00'));

INSERT INTO T_QST_BASE_M
    (parent_id, child_id, creation_request_key, title, content, deadline,
     is_teeny_score, verification_requirement, reward_amount,
     status, remaining_count, ended_at, created_at)
VALUES
    (@parent_id, @teen_id, UUID(), '장보기 돕기', '가족 장보기 목록을 정리해 주세요.',
     TIMESTAMP(DATE_ADD(@month1, INTERVAL 17 DAY), '20:00:00'), TRUE, 'TEXT_REQUIRED',
     4000, 'COMPLETED', 3, TIMESTAMP(DATE_ADD(@month1, INTERVAL 17 DAY), '18:00:00'),
     TIMESTAMP(DATE_ADD(@month1, INTERVAL 11 DAY), '09:00:00'));
SET @teen_quest1 := LAST_INSERT_ID();
INSERT INTO T_QST_VERIFY_L
    (quest_id, attempt_no, content, status, created_at, reviewed_at)
VALUES (@teen_quest1, 1, '장보기 목록을 정리했어요.', 'APPROVED',
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 17 DAY), '17:00:00'),
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 17 DAY), '18:00:00'));
INSERT INTO T_WLT_TRF_L
    (from_wallet_id, to_wallet_id, idempotency_key, amount, type, status, created_at)
VALUES (@parent_wallet, @teen_wallet, UUID(), 4000, 'QUEST_REWARD', 'COMPLETED',
        TIMESTAMP(DATE_ADD(@month1, INTERVAL 17 DAY), '18:00:00'));
SET @teen_reward1 := LAST_INSERT_ID();
INSERT INTO T_WLT_HIST_H
    (wallet_id, transfer_id, direction, amount, balance_after, description, created_at)
VALUES
    (@parent_wallet, @teen_reward1, 'DEBIT', 4000, 1015000, '리포트 퀘스트 보상 지급', TIMESTAMP(DATE_ADD(@month1, INTERVAL 17 DAY), '18:00:00')),
    (@teen_wallet, @teen_reward1, 'CREDIT', 4000, 90000, '리포트 퀘스트 보상', TIMESTAMP(DATE_ADD(@month1, INTERVAL 17 DAY), '18:00:00'));

INSERT INTO T_QST_BASE_M
    (parent_id, child_id, creation_request_key, title, content, deadline,
     is_teeny_score, verification_requirement, reward_amount,
     status, remaining_count, ended_at, created_at)
VALUES
    (@parent_id, @teen_id, UUID(), '저녁 설거지', '저녁 식사 후 설거지를 해 주세요.',
     TIMESTAMP(DATE_ADD(@month0, INTERVAL 7 DAY), '21:00:00'), TRUE, 'PHOTO_REQUIRED',
     2000, 'FAILED', 0, TIMESTAMP(DATE_ADD(@month0, INTERVAL 7 DAY), '21:00:00'),
     TIMESTAMP(DATE_ADD(@month0, INTERVAL 2 DAY), '09:00:00'));
SET @teen_quest2 := LAST_INSERT_ID();
INSERT INTO T_QST_VERIFY_L
    (quest_id, attempt_no, image_key, content, status, rejection_reason, created_at, reviewed_at)
VALUES (@teen_quest2, 1, CONCAT('report-demo/', @teen_quest2, '.jpg'),
        '설거지를 했어요.', 'REJECTED', '확인 사진에 설거지 결과가 보이지 않아요.',
        TIMESTAMP(DATE_ADD(@month0, INTERVAL 6 DAY), '20:00:00'),
        TIMESTAMP(DATE_ADD(@month0, INTERVAL 7 DAY), '09:00:00'));

-- Score history is chronological and ends at the member's current score.
INSERT INTO T_TNY_SCOREHIST_H
    (child_id, amount, score_after, event_code, event_key,
     description, reference_type, reference_id, created_at)
VALUES
    (@junior_id, 4, 604, 'SAVING_FIXED_INSTALLMENT_PAID', CONCAT('REPORT:SAVING:PAID:', @junior_saving, ':1'), '적금 1회차 납입 성공', 'SAVING_ENROLLMENT', @junior_saving, TIMESTAMP(DATE_ADD(@month2, INTERVAL 4 DAY), '09:00:00')),
    (@junior_id, 4, 608, 'SAVING_FIXED_INSTALLMENT_PAID', CONCAT('REPORT:SAVING:PAID:', @junior_saving, ':2'), '적금 2회차 납입 성공', 'SAVING_ENROLLMENT', @junior_saving, TIMESTAMP(DATE_ADD(@month1, INTERVAL 4 DAY), '09:00:00')),
    (@junior_id, 3, 611, 'QUEST_COMPLETED', CONCAT('REPORT:QUEST:COMPLETED:', @junior_quest1), '퀘스트 성공', 'QUEST', @junior_quest1, TIMESTAMP(DATE_ADD(@month1, INTERVAL 18 DAY), '18:00:00')),
    (@junior_id, 4, 615, 'SAVING_FIXED_INSTALLMENT_PAID', CONCAT('REPORT:SAVING:PAID:', @junior_saving, ':3'), '적금 3회차 납입 성공', 'SAVING_ENROLLMENT', @junior_saving, TIMESTAMP(DATE_ADD(@month0, INTERVAL 4 DAY), '09:00:00')),
    (@junior_id, 3, 618, 'QUEST_COMPLETED', CONCAT('REPORT:QUEST:COMPLETED:', @junior_quest2), '퀘스트 성공', 'QUEST', @junior_quest2, TIMESTAMP(DATE_ADD(@month0, INTERVAL 10 DAY), '18:00:00')),
    (@teen_id, 3, 703, 'QUEST_COMPLETED', CONCAT('REPORT:QUEST:COMPLETED:', @teen_quest1), '퀘스트 성공', 'QUEST', @teen_quest1, TIMESTAMP(DATE_ADD(@month1, INTERVAL 17 DAY), '18:00:00')),
    (@teen_id, -2, 701, 'QUEST_FAILED', CONCAT('REPORT:QUEST:FAILED:', @teen_quest2), '퀘스트 최종 실패', 'QUEST', @teen_quest2, TIMESTAMP(DATE_ADD(@month0, INTERVAL 7 DAY), '21:00:00')),
    (@teen_id, -4, 697, 'LOAN_INSTALLMENT_OVERDUE', CONCAT('REPORT:LOAN:OVERDUE:', @teen_loan, ':', DATE_FORMAT(@month0, '%Y-%m')), '대출 월별 상환 결과', 'LOAN_ENROLLMENT', @teen_loan, TIMESTAMP(DATE_ADD(@month0, INTERVAL 8 DAY), '00:05:00'));

COMMIT;

SELECT email, role, name
FROM T_MBR_INFO_M
WHERE email IN (
    'report-parent@naver.com',
    'report-junior@gmail.com',
    'report-teen@gmail.com',
    'report-empty@gmail.com'
)
ORDER BY id;
