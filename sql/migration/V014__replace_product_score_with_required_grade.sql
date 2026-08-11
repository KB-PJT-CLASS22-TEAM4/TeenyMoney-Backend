-- 예·적금은 누구나 가입할 수 있으므로 점수 가입조건을 제거한다.
-- min_teeny_score 컬럼을 참조하는 CHECK 제약조건을 먼저 제거해야 컬럼을 삭제할 수 있다.
ALTER TABLE `T_DPT_PROD_M`
    DROP CHECK `CK_DPT_PROD_M_MIN_TEENY_SCORE`,
    DROP COLUMN `min_teeny_score`;

-- 적금도 예금과 동일하게 점수 가입조건과 관련 CHECK 제약조건을 함께 제거한다.
ALTER TABLE `T_SVG_PROD_M`
    DROP CHECK `CK_SVG_PROD_M_MIN_TEENY_SCORE`,
    DROP COLUMN `min_teeny_score`;

-- 대출상품은 실시간 점수 대신 월간 적용 등급을 가입조건으로 사용한다.
-- 기존 상품 데이터를 변환하기 전이므로 required_grade_id는 임시로 NULL을 허용한다.
ALTER TABLE `T_LON_PROD_M`
    ADD COLUMN `required_grade_id` BIGINT NULL
        COMMENT '가입에 필요한 최소 월간 적용 등급'
        AFTER `max_amount`;

-- 기존 최소 티니점수를 등급의 시작 점수와 연결하여 요구등급 ID로 변환한다.
-- 예: 450점 -> 스타터(2), 650점 -> 플러스(3), 750점 -> 프로(4), 900점 -> 마스터(5)
UPDATE `T_LON_PROD_M` product
INNER JOIN `T_TNY_GRADE_A` grade
        ON product.`min_teeny_score` = grade.`min_score`
SET product.`required_grade_id` = grade.`grade_id`
-- MySQL Workbench의 safe update mode에서도 실행되도록 PK 조건을 명시한다.
WHERE product.`id` > 0;

-- 과거 비활성 샘플상품처럼 등급 시작점과 일치하지 않는 점수는 스타터로 보정한다.
-- 이 보정으로 required_grade_id를 NOT NULL로 변경할 때 NULL 데이터로 인한 실패를 방지한다.
UPDATE `T_LON_PROD_M`
SET `required_grade_id` = 2
-- required_grade_id는 아직 인덱스가 없으므로 PK 조건을 함께 사용한다.
WHERE `id` > 0
  AND `required_grade_id` IS NULL;

-- 데이터 변환이 완료되었으므로 새 요구등급 컬럼을 필수값으로 확정한다.
-- required_grade_id 조회와 등급 조인을 위한 인덱스 및 외래키를 추가한다.
-- 대출상품에서 참조 중인 등급은 삭제할 수 없도록 ON DELETE RESTRICT를 적용한다.
-- 마지막으로 더 이상 사용하지 않는 기존 점수 CHECK와 min_teeny_score 컬럼을 제거한다.
ALTER TABLE `T_LON_PROD_M`
    DROP CHECK `CK_LON_PROD_M_MIN_TEENY_SCORE`,
    MODIFY COLUMN `required_grade_id` BIGINT NOT NULL
        COMMENT '가입에 필요한 최소 월간 적용 등급',
    ADD INDEX `IX_LON_PROD_M_REQUIRED_GRADE_ID` (`required_grade_id`),
    ADD CONSTRAINT `FK_T_TNY_GRADE_A_TO_T_LON_PROD_M`
        FOREIGN KEY (`required_grade_id`)
        REFERENCES `T_TNY_GRADE_A` (`grade_id`)
        ON DELETE RESTRICT,
    DROP COLUMN `min_teeny_score`;
