-- MCC 정책에 WATCH 기준 횟수 컬럼(watch_threshold_count)을 추가하고, 자녀 2에게 기준 횟수 3회의 WATCH 정책을 등록한다.
ALTER TABLE `T_MCC_POLICY_M`
    ADD COLUMN `watch_threshold_count` SMALLINT NULL
        COMMENT '부모가 설정한 WATCH 기준 횟수. 기준 횟수 초과 시 강화 감점';

INSERT INTO `T_MCC_POLICY_M` (
    `parent_id`,
    `child_id`,
    `merchant_category_id`,
    `policy`,
    `watch_threshold_count`
) VALUES (
             1,
             2,
             1,
             'WATCH',
             3
         );