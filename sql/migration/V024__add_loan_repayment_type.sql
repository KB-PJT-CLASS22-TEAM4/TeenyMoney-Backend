-- 자녀가 언제든 원하는 금액으로 대출 원금을 조기상환할 수 있게 되면서
-- 스케줄러의 정규 회차 상환과 조기상환 이력을 구분할 필요가 생겼다.
ALTER TABLE `T_LON_REPAYHIST_H`
    ADD COLUMN `repayment_type` VARCHAR(10) NOT NULL DEFAULT 'SCHEDULED'
        COMMENT 'SCHEDULED(정규 회차 상환)/EARLY(조기상환)' AFTER `installment_no`;

ALTER TABLE `T_LON_REPAYHIST_H`
    ADD CONSTRAINT `CK_LON_REPAYHIST_H_REPAYMENT_TYPE`
    CHECK (`repayment_type` IN ('SCHEDULED', 'EARLY'));
