ALTER TABLE `T_DPT_PROD_M`
    ADD COLUMN `interest_calculation_type` VARCHAR(20) NULL
        COMMENT '이자 계산 방식(SIMPLE, COMPOUND)' AFTER `name`,
    ADD CONSTRAINT `chk_dpt_prod_interest_calculation_type`
        CHECK (`interest_calculation_type` IS NULL
            OR `interest_calculation_type` IN ('SIMPLE', 'COMPOUND'));