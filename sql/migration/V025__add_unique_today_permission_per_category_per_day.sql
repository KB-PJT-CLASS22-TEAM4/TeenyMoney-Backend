-- 같은 날 같은 카테고리로 "오늘만 허용"을 중복 요청하지 못하게 막는다.
-- 거절/만료된 건도 재신청을 막는 요구사항이라 상태 조건 없는 순수 UNIQUE로 건다.
--
-- 카테고리는 지금 T_TDP_REQCTGR_R(조인 테이블)에만 있는데, 실제로는 오늘만 허용 요청
-- 한 건당 카테고리 한 개(1:1)로만 쓰이고 있어(PermissionService.createPermission()이
-- 카테고리별로 T_TDP_REQ_L 행을 하나씩 따로 만듦), 유니크를 걸려면 child_id/created_at과
-- 같은 테이블에 있어야 하므로 merchant_category_id를 T_TDP_REQ_L로 옮긴다.
-- 다대다를 가정한 조인 테이블 자체가 더는 필요 없어서 백필 후 완전히 제거한다.

-- ---------------------------------------------------------------------
-- 1) merchant_category_id 컬럼 추가 (NULL 허용 상태로 시작, 기존 데이터 백필 후 NOT NULL 전환)
-- ---------------------------------------------------------------------
ALTER TABLE `T_TDP_REQ_L`
    ADD COLUMN `merchant_category_id` BIGINT NULL
        COMMENT '대상 업종 카테고리 (FK category). 요청 1건 = 카테고리 1개'
        AFTER `child_id`;

-- ---------------------------------------------------------------------
-- 2) 기존 행 백필: T_TDP_REQCTGR_R에 연결된 카테고리로 채운다
--    (한 요청에 카테고리가 여러 개 연결된 과거 데이터가 있다면 가장 먼저 연결된 것을 쓴다)
-- ---------------------------------------------------------------------
UPDATE `T_TDP_REQ_L` r
INNER JOIN (
    SELECT `today_permission_id`, MIN(`merchant_category_id`) AS `merchant_category_id`
    FROM `T_TDP_REQCTGR_R`
    GROUP BY `today_permission_id`
) rc ON rc.`today_permission_id` = r.`id`
SET r.`merchant_category_id` = rc.`merchant_category_id`
WHERE r.`merchant_category_id` IS NULL;

-- ---------------------------------------------------------------------
-- 3) NOT NULL 전환 + FK 제약
-- ---------------------------------------------------------------------
ALTER TABLE `T_TDP_REQ_L`
    MODIFY COLUMN `merchant_category_id` BIGINT NOT NULL
        COMMENT '대상 업종 카테고리 (FK category). 요청 1건 = 카테고리 1개';

ALTER TABLE `T_TDP_REQ_L`
    ADD CONSTRAINT `FK_T_MCC_CTGR_C_TO_T_TDP_REQ_L_1`
    FOREIGN KEY (`merchant_category_id`) REFERENCES `T_MCC_CTGR_C` (`id`)
    ON DELETE RESTRICT;

-- ---------------------------------------------------------------------
-- 4) created_at의 날짜 부분만 뽑아내는 생성 컬럼 추가 (유니크 인덱스에 쓰기 위함)
-- ---------------------------------------------------------------------
ALTER TABLE `T_TDP_REQ_L`
    ADD COLUMN `request_date` DATE
        GENERATED ALWAYS AS (DATE(`created_at`)) STORED
        COMMENT '생성 일자 (created_at에서 파생, 자녀+카테고리+일자 유니크용)';

-- ---------------------------------------------------------------------
-- 5) 자녀 + 카테고리 + 요청일자 유니크 제약
--    상태(거절/만료 포함)와 무관하게 하루에 같은 카테고리로는 한 번만 요청 가능
-- ---------------------------------------------------------------------
ALTER TABLE `T_TDP_REQ_L`
    ADD CONSTRAINT `UQ_T_TDP_REQ_L_CHILD_CATEGORY_DATE`
    UNIQUE (`child_id`, `merchant_category_id`, `request_date`);

-- ---------------------------------------------------------------------
-- 6) 조인 테이블 제거 — 백필이 끝나 더는 참조하지 않는다
-- ---------------------------------------------------------------------
DROP TABLE `T_TDP_REQCTGR_R`;
