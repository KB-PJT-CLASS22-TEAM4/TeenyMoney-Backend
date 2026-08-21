-- 부모가 자녀별로 설정한 월간 오늘만 허용 가능 일수를 연결 정보에 저장한다.
-- NULL인 기존·신규 연결은 기존 티니등급 기본 한도를 그대로 사용한다.
ALTER TABLE `T_MBR_CONN_R`
    ADD COLUMN `monthly_permission_day_limit` TINYINT NULL
        COMMENT '부모 설정 월간 오늘만 허용 가능 일수, NULL이면 티니등급 기본값'
        AFTER `status`;

ALTER TABLE `T_MBR_CONN_R`
    ADD CONSTRAINT `CK_MBR_CONN_R_PERMISSION_DAY_LIMIT`
        CHECK (`monthly_permission_day_limit` IS NULL
            OR `monthly_permission_day_limit` BETWEEN 0 AND 31);
