ALTER TABLE `T_WLT_BASE_M`
    ADD COLUMN `member_unique_key` BIGINT
        GENERATED ALWAYS AS (CASE WHEN `type` = 'MEMBER' THEN `member_id` ELSE NULL END) STORED
          COMMENT 'MEMBER 타입 지갑 중복 방지용 - type=MEMBER일 때만 member_id 값, 그 외 NULL';

ALTER TABLE `T_WLT_BASE_M`
    ADD CONSTRAINT `UK_WLT_BASE_M_MEMBER_UNIQUE` UNIQUE (`member_unique_key`);