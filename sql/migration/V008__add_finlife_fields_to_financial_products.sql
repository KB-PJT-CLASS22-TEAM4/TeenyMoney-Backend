-- 금융감독원 금융상품통합비교공시 상품을 기존 예금·적금 상품과 연결한다.
-- fin_co_no + fin_prdt_cd 조합으로 동일 외부 상품을 찾아 반복 동기화한다.
ALTER TABLE `T_DPT_PROD_M`
    ADD COLUMN `fin_co_no` VARCHAR(20) NULL COMMENT '금감원 금융회사 코드' AFTER `id`,
    ADD COLUMN `fin_prdt_cd` VARCHAR(100) NULL COMMENT '금감원 금융상품 코드' AFTER `fin_co_no`,
    ADD COLUMN `kor_co_nm` VARCHAR(100) NULL COMMENT '금융회사 한글명' AFTER `fin_prdt_cd`;

ALTER TABLE `T_SVG_PROD_M`
    ADD COLUMN `fin_co_no` VARCHAR(20) NULL COMMENT '금감원 금융회사 코드' AFTER `id`,
    ADD COLUMN `fin_prdt_cd` VARCHAR(100) NULL COMMENT '금감원 금융상품 코드' AFTER `fin_co_no`,
    ADD COLUMN `kor_co_nm` VARCHAR(100) NULL COMMENT '금융회사 한글명' AFTER `fin_prdt_cd`;

-- 서로 다른 금융회사가 같은 상품명을 사용할 수 있으므로 상품명 단독 UNIQUE를 제거한다.
ALTER TABLE `T_DPT_PROD_M`
    DROP INDEX `UQ_DPT_PROD_M_NAME`,
    ADD CONSTRAINT `UQ_DPT_PROD_M_FINLIFE`
        UNIQUE (`fin_co_no`, `fin_prdt_cd`);

ALTER TABLE `T_SVG_PROD_M`
    DROP INDEX `UQ_SVG_PROD_M_NAME`,
    ADD CONSTRAINT `UQ_SVG_PROD_M_FINLIFE`
        UNIQUE (`fin_co_no`, `fin_prdt_cd`);
