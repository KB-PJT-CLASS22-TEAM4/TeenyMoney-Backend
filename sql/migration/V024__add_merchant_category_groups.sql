-- ---------------------------------------------------------------------
-- 0) 사전 정리: 카테고리명 변경
-- ---------------------------------------------------------------------
UPDATE `T_MCC_CTGR_C` SET `name` = '외식' WHERE `name` = '외식·숙박';
UPDATE `T_MCC_CTGR_C` SET `name` = '스포츠·오락' WHERE `name` = '문화·여가';

-- ---------------------------------------------------------------------
-- 1) 자기참조 컬럼 추가 + FK 제약
-- ---------------------------------------------------------------------
ALTER TABLE `T_MCC_CTGR_C`
  ADD COLUMN `parent_id` BIGINT NULL COMMENT '상위 카테고리 아이디 (NULL이면 최상위)' AFTER `id`;

ALTER TABLE `T_MCC_CTGR_C`
  ADD CONSTRAINT `FK_T_MCC_CTGR_C_TO_T_MCC_CTGR_C_1`
  FOREIGN KEY (`parent_id`) REFERENCES `T_MCC_CTGR_C` (`id`) ON DELETE RESTRICT;

-- ---------------------------------------------------------------------
-- 2) 대분류 5건 삽입 ('기타'는 제외 — 기존 카테고리를 그대로 최상위로 씀)
-- ---------------------------------------------------------------------
INSERT INTO `T_MCC_CTGR_C` (`name`) VALUES
  ('음식'),
  ('쇼핑'),
  ('문화·여가'),
  ('생활'),
  ('유해업종');

-- ---------------------------------------------------------------------
-- 3) 기존 카테고리의 parent_id를 방금 넣은 대분류로 연결 (이름 기준)
--    같은 테이블을 참조하는 서브쿼리라 MySQL 제약상 파생테이블로 한번 감쌈
--    ('기타'는 손대지 않음 → parent_id NULL 그대로, 하위 없는 최상위 항목)
-- ---------------------------------------------------------------------
UPDATE `T_MCC_CTGR_C` SET `parent_id` = (
  SELECT `gid` FROM (SELECT `id` AS `gid` FROM `T_MCC_CTGR_C` WHERE `name` = '음식') AS `g`
) WHERE `name` IN ('편의점', '카페·디저트', '외식');

UPDATE `T_MCC_CTGR_C` SET `parent_id` = (
  SELECT `gid` FROM (SELECT `id` AS `gid` FROM `T_MCC_CTGR_C` WHERE `name` = '쇼핑') AS `g`
) WHERE `name` IN ('문구·도서·완구', '패션·뷰티', '온라인쇼핑', '생활용품·잡화');

UPDATE `T_MCC_CTGR_C` SET `parent_id` = (
  SELECT `gid` FROM (SELECT `id` AS `gid` FROM `T_MCC_CTGR_C` WHERE `name` = '문화·여가') AS `g`
) WHERE `name` IN ('게임', 'PC방·노래방', '영화·공연·테마파크', '스포츠·오락');

UPDATE `T_MCC_CTGR_C` SET `parent_id` = (
  SELECT `gid` FROM (SELECT `id` AS `gid` FROM `T_MCC_CTGR_C` WHERE `name` = '생활') AS `g`
) WHERE `name` IN ('대중교통', '통신', '학원·교육', '의료·건강', '생활서비스');

UPDATE `T_MCC_CTGR_C` SET `parent_id` = (
  SELECT `gid` FROM (SELECT `id` AS `gid` FROM `T_MCC_CTGR_C` WHERE `name` = '유해업종') AS `g`
) WHERE `name` IN ('유흥·성인업소', '사행성·도박', '성인숙박업', '일반숙박업');