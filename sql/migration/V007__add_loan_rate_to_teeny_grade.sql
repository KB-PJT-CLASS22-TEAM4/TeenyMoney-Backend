-- 티니점수 등급별 대출 이자율을 저장한다.
-- 대출을 이용할 수 없는 등급은 NULL로 표현한다.
ALTER TABLE `T_TNY_GRADE_A`
    ADD COLUMN `loan_rate` DECIMAL(5, 2) NULL
        COMMENT '등급별 대출 이자율(%). 대출 불가 등급은 NULL'
        AFTER `bonus_rate`;

-- 기존 등급 데이터에 확정된 대출 이자율을 반영한다.
UPDATE T_TNY_GRADE_A
SET `loan_rate` = CASE `grade_id`
    WHEN 1 THEN NULL
    WHEN 2 THEN 7.00
    WHEN 3 THEN 5.00
    WHEN 4 THEN 3.50
    WHEN 5 THEN 2.00
    ELSE `loan_rate`
END
WHERE `grade_id` IN (1, 2, 3, 4, 5);
