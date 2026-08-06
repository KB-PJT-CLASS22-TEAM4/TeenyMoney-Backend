-- MCC 정책에 WATCH 기준 횟수 컬럼(watch_threshold_count)을 추가한다.
--
-- 예시 정책 행(부모 1 / 자녀 2 / 편의점 WATCH, 기준 3회)은 seed/01_seed_valid_data.sql 로 옮겼다.
-- 특정 회원 아이디를 가정한 테스트 데이터라 마이그레이션에 두면 두 가지가 깨진다.
--   1) 새 DB에서는 회원도 업종 카테고리도 없는 시점에 실행되어 FK 로 실패한다.
--   2) EC2 에 적용하면 실재하지 않는 회원 기준의 정책이 공유 DB에 들어간다
--      (README: "seed는 EC2에 적용하지 않습니다").
ALTER TABLE `T_MCC_POLICY_M`
    ADD COLUMN `watch_threshold_count` SMALLINT NULL
        COMMENT '부모가 설정한 WATCH 기준 횟수. 기준 횟수 초과 시 강화 감점';

