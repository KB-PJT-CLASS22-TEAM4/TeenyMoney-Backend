-- 퀘스트 확정 설계 반영
-- 적용 전 백업과 대상 환경의 기존 퀘스트 데이터 검증이 필요하다.

ALTER TABLE `T_QST_BASE_M`
    DROP CHECK `CK_QST_BASE_M_REWARD_AMOUNT`,
    DROP CHECK `CK_QST_BASE_M_REWARD_TEENY_SCORE`,
    DROP CHECK `CK_QST_BASE_M_STATUS`;

ALTER TABLE `T_QST_BASE_M`
    ADD COLUMN `creation_request_key` CHAR(36) NULL COMMENT '일괄 생성 요청 식별 키(UUID)' AFTER `child_id`,
    MODIFY COLUMN `title` VARCHAR(50) NOT NULL COMMENT '퀘스트 제목',
    ADD COLUMN `verification_requirement` VARCHAR(30) NOT NULL DEFAULT 'FREE'
        COMMENT '인증 방식: FREE/PHOTO_REQUIRED/TEXT_REQUIRED/ANY_REQUIRED' AFTER `is_teeny_score`,
    DROP COLUMN `reward_teeny_score`,
    ADD COLUMN `accepted_at` DATETIME NULL COMMENT '자녀 수락 일시' AFTER `remaining_count`,
    ADD COLUMN `decline_reason_code` VARCHAR(30) NULL COMMENT '자녀 거절 사유 코드' AFTER `accepted_at`,
    ADD COLUMN `decline_reason_detail` VARCHAR(500) NULL COMMENT '자녀 거절 상세 사유' AFTER `decline_reason_code`,
    CHANGE COLUMN `completed_at` `ended_at` DATETIME NULL COMMENT '최종 종료 일시';

UPDATE `T_QST_BASE_M`
SET `content` = `title`
WHERE `content` IS NULL OR CHAR_LENGTH(TRIM(`content`)) = 0;

UPDATE `T_QST_BASE_M`
SET `creation_request_key` = LOWER(UUID())
WHERE `creation_request_key` IS NULL;

ALTER TABLE `T_QST_BASE_M`
    MODIFY COLUMN `content` VARCHAR(500) NOT NULL COMMENT '상세 내용',
    MODIFY COLUMN `creation_request_key` CHAR(36) NOT NULL COMMENT '일괄 생성 요청 식별 키(UUID)',
    ADD CONSTRAINT `UQ_QST_BASE_M_CREATION_CHILD`
        UNIQUE (`parent_id`, `creation_request_key`, `child_id`),
    ADD CONSTRAINT `CK_QST_BASE_M_REWARD_AMOUNT`
        CHECK (`reward_amount` = 0 OR `reward_amount` >= 100),
    ADD CONSTRAINT `CK_QST_BASE_M_REWARD_PRESENT`
        CHECK (`reward_amount` > 0 OR `is_teeny_score` = TRUE),
    ADD CONSTRAINT `CK_QST_BASE_M_VERIFICATION_REQUIREMENT`
        CHECK (`verification_requirement` IN ('FREE', 'PHOTO_REQUIRED', 'TEXT_REQUIRED', 'ANY_REQUIRED')),
    ADD CONSTRAINT `CK_QST_BASE_M_STATUS`
        CHECK (`status` IN ('AVAILABLE', 'IN_PROGRESS', 'PENDING', 'COMPLETED', 'FAILED', 'EXPIRED', 'DECLINED'));

CREATE INDEX `IX_QST_BASE_M_DEADLINE`
    ON `T_QST_BASE_M` (`status`, `deadline`, `id`);

ALTER TABLE `T_QST_VERIFY_L`
    CHANGE COLUMN `image_url` `image_key` VARCHAR(1024) NULL COMMENT '비공개 S3 객체 키',
    ADD COLUMN `attempt_no` SMALLINT NULL COMMENT '퀘스트별 인증 시도 번호' AFTER `quest_id`,
    MODIFY COLUMN `rejection_reason` VARCHAR(500) NULL COMMENT '반려 사유';

WITH ranked AS (
    SELECT
        `id`,
        ROW_NUMBER() OVER (
            PARTITION BY `quest_id`
            ORDER BY `created_at` ASC, `id` ASC
        ) AS `attempt_no`
    FROM `T_QST_VERIFY_L`
)
UPDATE `T_QST_VERIFY_L` verification
JOIN ranked ON ranked.`id` = verification.`id`
SET verification.`attempt_no` = ranked.`attempt_no`;

ALTER TABLE `T_QST_VERIFY_L`
    MODIFY COLUMN `attempt_no` SMALLINT NOT NULL COMMENT '퀘스트별 인증 시도 번호',
    DROP INDEX `IX_QST_VERIFY_L_N01`,
    ADD CONSTRAINT `UQ_QST_VERIFY_L_ATTEMPT` UNIQUE (`quest_id`, `attempt_no`),
    ADD CONSTRAINT `CK_QST_VERIFY_L_REJECTION_REASON`
        CHECK (`status` <> 'REJECTED' OR (
            `rejection_reason` IS NOT NULL
            AND CHAR_LENGTH(TRIM(`rejection_reason`)) BETWEEN 1 AND 500
        ));
