-- 하나의 금감원 적금 상품에 적립 유형과 이자 계산 방식이 다른 옵션이 함께 있을 수 있다.
-- 외부 상품, 적립 유형, 이자 계산 방식의 조합을 각각 독립된 상품 행으로 저장한다.
ALTER TABLE `T_SVG_PROD_M`
DROP INDEX `UQ_SVG_PROD_M_FINLIFE`,
    ADD CONSTRAINT `UQ_SVG_PROD_M_FINLIFE_OPTION`
        UNIQUE (
            `fin_co_no`,
            `fin_prdt_cd`,
            `savings_type`,
            `interest_calculation_type`
        );