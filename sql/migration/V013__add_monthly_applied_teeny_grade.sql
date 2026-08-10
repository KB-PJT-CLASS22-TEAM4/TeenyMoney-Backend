-- 티니점수는 즉시 변경하되, 등급과 등급 혜택은 월 단위로 고정하기 위한 컬럼을 추가한다.
ALTER TABLE `T_MBR_INFO_M`
    ADD COLUMN `applied_grade_id` BIGINT NULL
        COMMENT '현재 적용 중인 월간 티니등급 (자녀만 사용)'
        AFTER `teeny_score`,
    ADD COLUMN `grade_applied_at` DATETIME NULL
        COMMENT '현재 티니등급을 적용한 일시'
        AFTER `applied_grade_id`,
    ADD INDEX `IX_MBR_INFO_M_APPLIED_GRADE_ID` (`applied_grade_id`),
    ADD CONSTRAINT `FK_T_TNY_GRADE_A_TO_T_MBR_INFO_M_1`
        FOREIGN KEY (`applied_grade_id`)
        REFERENCES `T_TNY_GRADE_A` (`grade_id`)
        ON DELETE RESTRICT;

-- 기존 자녀는 마이그레이션 시점의 티니점수를 기준으로 최초 등급을 부여한다.
UPDATE `T_MBR_INFO_M` AS member
    JOIN `T_TNY_GRADE_A` AS grade
ON member.`teeny_score` BETWEEN grade.`min_score` AND grade.`max_score`
    SET member.`applied_grade_id` = grade.`grade_id`,
        member.`grade_applied_at` = CURRENT_TIMESTAMP
WHERE member.`role` = 'CHILD'
  AND member.`id` > 0;

