-- 상품 출처와 부모 생성 상품의 범위를 저장한다.
ALTER TABLE `T_DPT_PROD_M`
    ADD COLUMN `product_source` VARCHAR(20) NOT NULL DEFAULT 'TEENY'
    COMMENT '상품 출처: TEENY, FINLIFE, PARENT',
    ADD COLUMN `created_by_parent_id` BIGINT NULL
        COMMENT '상품을 생성한 부모 회원 ID',
    ADD COLUMN `target_child_id` BIGINT NULL
        COMMENT '부모 생성 상품의 대상 자녀 회원 ID';

ALTER TABLE `T_SVG_PROD_M`
    ADD COLUMN `product_source` VARCHAR(20) NOT NULL DEFAULT 'TEENY'
    COMMENT '상품 출처: TEENY, FINLIFE, PARENT',
    ADD COLUMN `created_by_parent_id` BIGINT NULL
        COMMENT '상품을 생성한 부모 회원 ID',
    ADD COLUMN `target_child_id` BIGINT NULL
        COMMENT '부모 생성 상품의 대상 자녀 회원 ID';

ALTER TABLE `T_LON_PROD_M`
    ADD COLUMN `product_source` VARCHAR(20) NOT NULL DEFAULT 'TEENY'
    COMMENT '상품 출처: TEENY, FINLIFE, PARENT',
    ADD COLUMN `created_by_parent_id` BIGINT NULL
        COMMENT '상품을 생성한 부모 회원 ID',
    ADD COLUMN `target_child_id` BIGINT NULL
        COMMENT '부모 생성 상품의 대상 자녀 회원 ID',
DROP INDEX `UQ_LON_PROD_M_NAME`;

-- 기존 금감원 연동 상품만 FINLIFE로 변경한다.
UPDATE `T_DPT_PROD_M`
SET `product_source` = 'FINLIFE'
WHERE `id` > 0
  AND `fin_co_no` IS NOT NULL
  AND `fin_prdt_cd` IS NOT NULL;

UPDATE `T_SVG_PROD_M`
SET `product_source` = 'FINLIFE'
WHERE `id` > 0
  AND `fin_co_no` IS NOT NULL
  AND `fin_prdt_cd` IS NOT NULL;


-- 부모 생성 대출은 부모가 선택한 가입기간들을 허용하고
-- 선택한 모든 기간에 동일한 기본금리를 적용한다.
ALTER TABLE `T_LON_PROD_M`
    ADD COLUMN `available_1m` BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT '1개월 가입 가능 여부'
        AFTER `base_rate`,
    ADD COLUMN `available_3m` BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT '3개월 가입 가능 여부'
        AFTER `available_1m`,
    ADD COLUMN `available_6m` BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT '6개월 가입 가능 여부'
        AFTER `available_3m`,
    ADD COLUMN `available_12m` BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT '12개월 가입 가능 여부'
        AFTER `available_6m`;