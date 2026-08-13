-- Money report demo acceptance check.
-- Required execution order for a new local database:
-- schema -> every migration through V020 in filename order ->
-- 01_seed_valid_data.sql -> 02_seed_money_report_demo.sql -> this file.
-- V006_1 supplies grade rows before grade-dependent migrations. V019 is absent.
-- Success condition: the final query returns zero rows.

SET @current_month := DATE_FORMAT(CURDATE(), '%Y-%m-01');
SET @previous_month := DATE_FORMAT(DATE_SUB(@current_month, INTERVAL 1 MONTH), '%Y-%m-01');
SET @two_months_ago := DATE_FORMAT(DATE_SUB(@current_month, INTERVAL 2 MONTH), '%Y-%m-01');
SET @next_month := DATE_ADD(@current_month, INTERVAL 1 MONTH);

DROP TEMPORARY TABLE IF EXISTS expected_report_spending;
CREATE TEMPORARY TABLE expected_report_spending (
    email VARCHAR(100) NOT NULL,
    month_start DATE NOT NULL,
    expected_amount BIGINT NOT NULL,
    expected_count INT NOT NULL,
    PRIMARY KEY (email, month_start)
);

INSERT INTO expected_report_spending
    (email, month_start, expected_amount, expected_count)
VALUES
    ('report-junior@gmail.com', @two_months_ago, 30000, 3),
    ('report-junior@gmail.com', @previous_month, 35000, 4),
    ('report-junior@gmail.com', @current_month, 26000, 3),
    ('report-teen@gmail.com', @two_months_ago, 40000, 3),
    ('report-teen@gmail.com', @previous_month, 52000, 4),
    ('report-teen@gmail.com', @current_month, 47000, 3),
    ('report-empty@gmail.com', @two_months_ago, 0, 0),
    ('report-empty@gmail.com', @previous_month, 0, 0),
    ('report-empty@gmail.com', @current_month, 0, 0);

WITH actual_spending AS (
    SELECT member.email,
           DATE_FORMAT(payment.created_at, '%Y-%m-01') AS month_start,
           SUM(payment.amount) AS amount,
           COUNT(*) AS payment_count
    FROM T_MBR_INFO_M member
    JOIN T_WLT_BASE_M wallet
      ON wallet.member_id = member.id AND wallet.type = 'MEMBER'
    JOIN T_PAY_TRAN_L payment
      ON payment.wallet_id = wallet.id AND payment.status = 'SUCCESS'
    WHERE member.email IN (
        'report-junior@gmail.com',
        'report-teen@gmail.com',
        'report-empty@gmail.com'
    )
      AND payment.created_at >= @two_months_ago
      AND payment.created_at < @next_month
    GROUP BY member.email, DATE_FORMAT(payment.created_at, '%Y-%m-01')
),
violations AS (
    SELECT 'DEMO_MEMBER_MISSING' AS broken_rule,
           expected.email AS detail
    FROM (
        SELECT 'report-parent@naver.com' AS email
        UNION ALL SELECT 'report-junior@gmail.com'
        UNION ALL SELECT 'report-teen@gmail.com'
        UNION ALL SELECT 'report-empty@gmail.com'
    ) expected
    LEFT JOIN T_MBR_INFO_M member ON member.email = expected.email
    WHERE member.id IS NULL

    UNION ALL

    SELECT 'MONTHLY_SPENDING_MISMATCH',
           CONCAT(expected.email, ' ', expected.month_start,
                  ': expected=', expected.expected_amount, '/', expected.expected_count,
                  ', actual=', COALESCE(actual.amount, 0), '/', COALESCE(actual.payment_count, 0))
    FROM expected_report_spending expected
    LEFT JOIN actual_spending actual
      ON actual.email = expected.email
     AND actual.month_start = expected.month_start
    WHERE COALESCE(actual.amount, 0) <> expected.expected_amount
       OR COALESCE(actual.payment_count, 0) <> expected.expected_count

    UNION ALL

    SELECT 'PERMISSION_SCENARIO_MISMATCH',
           CONCAT(member.email, ': approved=', SUM(permission.status = 'APPROVED'),
                  ', rejected=', SUM(permission.status = 'REJECTED'),
                  ', expired=', SUM(permission.status = 'EXPIRED'))
    FROM T_MBR_INFO_M member
    LEFT JOIN T_TDP_REQ_L permission ON permission.child_id = member.id
    WHERE member.email IN ('report-junior@gmail.com', 'report-teen@gmail.com')
    GROUP BY member.id, member.email
    HAVING (member.email = 'report-junior@gmail.com'
            AND NOT (SUM(permission.status = 'APPROVED') = 1
                     AND SUM(permission.status = 'REJECTED') = 1
                     AND SUM(permission.status = 'EXPIRED') = 0))
        OR (member.email = 'report-teen@gmail.com'
            AND NOT (SUM(permission.status = 'APPROVED') = 1
                     AND SUM(permission.status = 'REJECTED') = 0
                     AND SUM(permission.status = 'EXPIRED') = 1))

    UNION ALL

    SELECT 'JUNIOR_SAVING_MISMATCH',
           CONCAT('paid_count=', COUNT(payment.id),
                  ', paid_amount=', COALESCE(SUM(payment.paid_amount), 0),
                  ', wallet_balance=', saving_wallet.balance)
    FROM T_MBR_INFO_M member
    JOIN T_SVG_ENROLL_M enrollment ON enrollment.child_id = member.id
    JOIN T_WLT_BASE_M saving_wallet ON saving_wallet.id = enrollment.wallet_id
    LEFT JOIN T_SVG_PAYHIST_H payment
      ON payment.saving_enrollment_id = enrollment.id
     AND payment.status = 'PAID'
    WHERE member.email = 'report-junior@gmail.com'
    GROUP BY enrollment.id, saving_wallet.balance
    HAVING COUNT(payment.id) <> 3
        OR COALESCE(SUM(payment.paid_amount), 0) <> 30000
        OR saving_wallet.balance <> 30000

    UNION ALL

    SELECT 'TEEN_DEPOSIT_MISMATCH',
           CONCAT('status=', enrollment.status, ', balance=', deposit_wallet.balance)
    FROM T_MBR_INFO_M member
    JOIN T_DPT_ENROLL_M enrollment ON enrollment.child_id = member.id
    JOIN T_WLT_BASE_M deposit_wallet ON deposit_wallet.id = enrollment.wallet_id
    WHERE member.email = 'report-teen@gmail.com'
      AND (enrollment.status <> 'ACTIVE' OR deposit_wallet.balance <> 50000)

    UNION ALL

    SELECT 'TEEN_LOAN_MISMATCH',
           CONCAT('status=', enrollment.status,
                  ', outstanding=', enrollment.outstanding_principal,
                  ', paid_count=', enrollment.paid_count,
                  ', paid_principal=', COALESCE(SUM(repayment.paid_principal_amount), 0))
    FROM T_MBR_INFO_M member
    JOIN T_LON_ENROLL_M enrollment ON enrollment.child_id = member.id
    LEFT JOIN T_LON_REPAYHIST_H repayment
      ON repayment.loan_enrollment_id = enrollment.id
     AND repayment.status = 'PAID'
    WHERE member.email = 'report-teen@gmail.com'
    GROUP BY enrollment.id, enrollment.status,
             enrollment.outstanding_principal, enrollment.paid_count
    HAVING enrollment.status <> 'OVERDUE'
        OR enrollment.outstanding_principal <> 40000
        OR enrollment.paid_count <> 2
        OR COALESCE(SUM(repayment.paid_principal_amount), 0) <> 20000

    UNION ALL

    SELECT 'QUEST_REWARD_MISMATCH',
           CONCAT(member.email, ': completed=', SUM(quest.status = 'COMPLETED'),
                  ', reward=', SUM(CASE WHEN quest.status = 'COMPLETED'
                                       THEN quest.reward_amount ELSE 0 END))
    FROM T_MBR_INFO_M member
    LEFT JOIN T_QST_BASE_M quest ON quest.child_id = member.id
    WHERE member.email IN ('report-junior@gmail.com', 'report-teen@gmail.com')
    GROUP BY member.id, member.email
    HAVING (member.email = 'report-junior@gmail.com'
            AND NOT (SUM(quest.status = 'COMPLETED') = 2
                     AND SUM(CASE WHEN quest.status = 'COMPLETED'
                                  THEN quest.reward_amount ELSE 0 END) = 8000))
        OR (member.email = 'report-teen@gmail.com'
            AND NOT (SUM(quest.status = 'COMPLETED') = 1
                     AND SUM(CASE WHEN quest.status = 'COMPLETED'
                                  THEN quest.reward_amount ELSE 0 END) = 4000))

    UNION ALL

    SELECT 'TEENY_SCORE_CURRENT_VALUE_MISMATCH',
           CONCAT(member.email, ': member=', member.teeny_score,
                  ', latest_history=', history.score_after)
    FROM T_MBR_INFO_M member
    JOIN T_TNY_SCOREHIST_H history
      ON history.child_id = member.id
     AND history.id = (
         SELECT history2.id
         FROM T_TNY_SCOREHIST_H history2
         WHERE history2.child_id = member.id
         ORDER BY history2.created_at DESC, history2.id DESC
         LIMIT 1
     )
    WHERE member.email IN ('report-junior@gmail.com', 'report-teen@gmail.com')
      AND member.teeny_score <> history.score_after

    UNION ALL

    SELECT 'WALLET_BALANCE_MISMATCH',
           CONCAT(member.email, '/', wallet.type,
                  ': wallet=', wallet.balance,
                  ', latest_history=', history.balance_after)
    FROM T_MBR_INFO_M member
    JOIN T_WLT_BASE_M wallet ON wallet.member_id = member.id
    JOIN T_WLT_HIST_H history
      ON history.wallet_id = wallet.id
     AND history.id = (
         SELECT history2.id
         FROM T_WLT_HIST_H history2
         WHERE history2.wallet_id = wallet.id
         ORDER BY history2.created_at DESC, history2.id DESC
         LIMIT 1
     )
    WHERE member.email LIKE 'report-%@gmail.com'
      AND wallet.balance <> history.balance_after

    UNION ALL

    SELECT 'COMPLETED_TRANSFER_LEDGER_COUNT_MISMATCH',
           CONCAT('transfer=', transfer.id, ', ledger_count=', COUNT(history.id))
    FROM T_WLT_TRF_L transfer
    JOIN T_WLT_BASE_M source_wallet ON source_wallet.id = transfer.from_wallet_id
    JOIN T_MBR_INFO_M source_member ON source_member.id = source_wallet.member_id
    LEFT JOIN T_WLT_HIST_H history ON history.transfer_id = transfer.id
    WHERE source_member.email LIKE 'report-%@gmail.com'
      AND transfer.status = 'COMPLETED'
    GROUP BY transfer.id
    HAVING COUNT(history.id) <> 2

    UNION ALL

    SELECT 'PAYMENT_LEDGER_MISSING', CONCAT('payment=', payment.id)
    FROM T_PAY_TRAN_L payment
    JOIN T_WLT_BASE_M wallet ON wallet.id = payment.wallet_id
    JOIN T_MBR_INFO_M member ON member.id = wallet.member_id
    LEFT JOIN T_WLT_HIST_H history
      ON history.payment_id = payment.id AND history.direction = 'DEBIT'
    WHERE member.email IN ('report-junior@gmail.com', 'report-teen@gmail.com')
      AND payment.status = 'SUCCESS'
      AND history.id IS NULL
)
SELECT broken_rule, detail
FROM violations
ORDER BY broken_rule, detail;

DROP TEMPORARY TABLE expected_report_spending;
