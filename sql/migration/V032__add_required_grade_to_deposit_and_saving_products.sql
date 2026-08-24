-- 부모 생성 예·적금에 최소 가입 등급을 설정한다.
-- 기존 티니·금감원 상품은 NULL(등급 제한 없음)을 유지한다.
ALTER TABLE `T_DPT_PROD_M`
    ADD COLUMN `required_grade_id` BIGINT NULL
        COMMENT '최소 가입 요구등급. NULL이면 제한 없음'
        AFTER `max_amount`,
    ADD INDEX `IX_DPT_PROD_M_REQUIRED_GRADE_ID` (`required_grade_id`),
    ADD CONSTRAINT `FK_T_TNY_GRADE_A_TO_T_DPT_PROD_M_1`
        FOREIGN KEY (`required_grade_id`)
        REFERENCES `T_TNY_GRADE_A` (`grade_id`)
        ON DELETE RESTRICT;

ALTER TABLE `T_SVG_PROD_M`
    ADD COLUMN `required_grade_id` BIGINT NULL
        COMMENT '최소 가입 요구등급. NULL이면 제한 없음'
        AFTER `max_month_amount`,
    ADD INDEX `IX_SVG_PROD_M_REQUIRED_GRADE_ID` (`required_grade_id`),
    ADD CONSTRAINT `FK_T_TNY_GRADE_A_TO_T_SVG_PROD_M_1`
        FOREIGN KEY (`required_grade_id`)
        REFERENCES `T_TNY_GRADE_A` (`grade_id`)
        ON DELETE RESTRICT;
