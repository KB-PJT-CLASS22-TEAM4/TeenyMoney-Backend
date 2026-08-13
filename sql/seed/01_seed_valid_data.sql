-- ============================================================
-- 정상 시드 데이터 (렌네이밍된 테이블명 기준)
--
-- 실행 순서:
--   1. schema/teenymoney_schema_renamed.sql
--   2. migration/V001 ~ V020 전부, 파일명 순서대로 (V006 다음 V006_1, V019 없음)
--   3. seed/01_seed_valid_data.sql (이 파일)
--
-- 세 단계 모두 에러 없이 끝나야 한다. 하나라도 실패하면 제약조건이나 순서에 버그가 있는 것이다.
--
-- 전부 성공해야 정상 (하나라도 실패하면 제약조건에 버그가 있는 것)
--
-- 약관 원본(T_MBR_AGRMT_M)은 V001 이 직접 INSERT 하므로 여기서 다시 넣지 않는다.
-- 넣으면 UQ_MBR_AGRMT_M_CODE_VERSION 에 걸린다.
-- ============================================================

-- T_MBR_INFO_M: 부모 1, 자녀 2 (부모 1명 고정 구조)
--
-- 아래 세 값은 인증 API(하위3)의 규칙에 맞춘 것이다. 임의로 되돌리지 말 것.
--
--  1) phone_number 는 숫자만 저장한다.
--     서버가 가입 시 하이픈을 제거해 저장하므로 여기에 하이픈을 넣으면, 같은 번호인데
--     문자열이 달라져 사전 중복 검사와 UNIQUE 제약을 둘 다 통과한다. 즉 같은 번호로
--     계정이 두 개 만들어진다. 표시용 하이픈은 프론트가 붙인다.
--
--  2) password 는 실제 BCrypt 해시다. 평문 자리표시자를 쓰면 로그인이 불가능하다.
--     BCryptPasswordEncoder.matches() 가 해시 형식을 보고 즉시 false를 반환하기 때문이다.
--     평문: Local1234!   (세 회원 공통)
--     해시를 새로 뽑으려면:
--       ./gradlew test --tests "*TokenPrinterTest.printSeedPasswordHash" --rerun-tasks -i
--     BCrypt는 salt가 매번 달라 실행마다 값이 바뀐다. 어느 쪽이든 유효하다.
--
--  3) PARENT 의 teeny_score 는 NULL 이다. 컬럼을 생략하면 DB 기본값 600이 들어간다.
--     등급 판정이 T_TNY_GRADE_A 를 BETWEEN 으로 조회하므로 600이면 부모가
--     '스타터'(450~649)로 매칭되어 우대금리 1.00%와 오늘만허용 3회를 받는다.
--     스키마 주석도 "(자녀만 사용)"이고 CHECK 가 IS NULL 을 허용한다.
INSERT INTO `T_MBR_INFO_M` (`id`, `role`, `name`, `birth_date`, `phone_number`, `email`, `password`, `teeny_score`, `customer_key`, `status`)
VALUES (1, 'PARENT', '김부모', '1985-03-11', '01011111111', 'parent1@naver.com',
        '$2a$10$Ii6qH9kVC2z.mkEdiVas9.dN9yr/wZXPoSUgExNjp7N9Dra8avcSy',
        NULL, 'toss-customer-uuid-1', 'ACTIVE');

-- payment_password 는 아직 자리표시자다. 결제 비밀번호는 결제 도메인 이슈 범위이고
-- 인증 API가 읽지 않으므로 그대로 둔다. 결제 도메인 작업 시 실제 해시로 교체한다.
--
-- 자녀2의 teeny_score 는 610 이다. 600(기본값)이 아니라 610인 이유는 아래 T_TNY_SCOREHIST_H 에
-- +10 이력이 있고 그 행의 score_after 가 610이기 때문이다. 이 컬럼은 이력의 최신 score_after 를
-- 캐시한 값이라, 둘이 어긋나면 "점수 이벤트 적용 후 member.teeny_score == 최신 score_after" 를
-- 검증하는 테스트가 시작부터 깨진 값을 물고 간다. 한쪽을 바꾸면 반드시 다른 쪽도 바꾼다.
-- 610은 '스타터'(450~649) 구간이며 월간 적용 등급도 같은 등급으로 고정한다.
INSERT INTO `T_MBR_INFO_M` (`id`, `role`, `name`, `birth_date`, `phone_number`, `email`, `password`, `payment_password`, `teeny_score`, `applied_grade_id`, `grade_applied_at`, `status`)
VALUES
(2, 'CHILD', '김첫째', '2013-05-20', '01022222222', 'child1@gmail.com',
 '$2a$10$Ii6qH9kVC2z.mkEdiVas9.dN9yr/wZXPoSUgExNjp7N9Dra8avcSy', 'hashed_pay_pw', 610,
 (SELECT `grade_id` FROM `T_TNY_GRADE_A` WHERE 610 BETWEEN `min_score` AND `max_score`), '2026-08-01 00:00:00', 'ACTIVE'),
(3, 'CHILD', '김둘째', '2015-09-02', '01033333333', 'child2@gmail.com',
 '$2a$10$Ii6qH9kVC2z.mkEdiVas9.dN9yr/wZXPoSUgExNjp7N9Dra8avcSy', 'hashed_pay_pw', 600,
 (SELECT `grade_id` FROM `T_TNY_GRADE_A` WHERE 600 BETWEEN `min_score` AND `max_score`), '2026-08-01 00:00:00', 'ACTIVE');

-- T_MBR_CONN_R: 부모-자녀 연동 (부모 1명 고정이므로 자녀당 1건)
INSERT INTO `T_MBR_CONN_R` (`id`, `parent_id`, `child_id`, `status`)
VALUES
(1, 1, 2, 'ACTIVE'),
(2, 1, 3, 'ACTIVE');

-- [V001] T_MBR_LEGAL_GUARDIAN_M: 만 14세 미만 자녀의 법정대리인 인증 근거
--
-- 두 자녀 모두 만 14세 미만이다(2013-05-20 → 13세, 2015-09-02 → 10세, 2026-08-06 기준).
-- 가입 흐름상 이 나이대는 legalGuardianConsentToken 없이는 가입 자체가 불가능하므로,
-- 이 행이 없는 자녀 회원은 서버가 만들 수 없는 상태다. 만 14세 이상 케이스가 필요하면
-- 자녀를 추가하고 이 표에서 빼는 게 아니라 생년월일을 14년 전보다 이르게 잡아야 한다.
--
-- 이 테이블은 '비회원' 법정대리인용이라 회원 1(김부모)과 별개다. 컬럼에 member FK 가 없고
-- 이름·번호를 직접 들고 있는 이유가 그것이다. 번호를 회원 1과 같게 두면 회원인 부모가
-- 비회원 대리인으로도 등록된 모순된 데이터가 된다.
INSERT INTO `T_MBR_LEGAL_GUARDIAN_M`
	(`id`, `child_member_id`, `name`, `phone_number`, `relationship`, `verification_method`, `verification_reference`, `verified_at`)
VALUES
(1, 2, '김보호', '01044444444', 'MOTHER', 'SMS_TEST', 'lg-verify-child-2', '2026-06-01 10:00:00'),
(2, 3, '김보호', '01044444444', 'MOTHER', 'SMS_TEST', 'lg-verify-child-3', '2026-06-01 10:05:00');

-- [V001] T_MBR_AGRMT_H: 회원별 필수 약관 동의 이력
--
-- agreement_id 를 1, 2 로 박지 않고 code+version 으로 찾는 이유는 T_MBR_AGRMT_M 의 id 가
-- V001 의 AUTO_INCREMENT 채번 결과라서다. 약관이 추가되거나 재실행되면 번호가 밀린다.
--
-- actor_type 은 부모가 SELF, 만 14세 미만 자녀는 LEGAL_GUARDIAN 이다. 자녀 행의
-- actor_member_id 가 NULL 인 것은 오류가 아니라 위 표의 비회원 대리인이 동의했다는 뜻이고,
-- 그 근거는 verification_reference 로 T_MBR_LEGAL_GUARDIAN_M 과 이어진다.
INSERT INTO `T_MBR_AGRMT_H`
	(`id`, `member_id`, `agreement_id`, `status`, `actor_type`, `actor_member_id`, `verification_method`, `verification_reference`, `created_at`)
VALUES
(1, 1, (SELECT `id` FROM `T_MBR_AGRMT_M` WHERE `code` = 'SERVICE_TERMS' AND `version` = '1.0'),
 'AGREED', 'SELF', 1, NULL, NULL, '2026-06-01 09:00:00'),
(2, 1, (SELECT `id` FROM `T_MBR_AGRMT_M` WHERE `code` = 'PRIVACY' AND `version` = '1.0'),
 'AGREED', 'SELF', 1, NULL, NULL, '2026-06-01 09:00:00'),
(3, 2, (SELECT `id` FROM `T_MBR_AGRMT_M` WHERE `code` = 'SERVICE_TERMS' AND `version` = '1.0'),
 'AGREED', 'LEGAL_GUARDIAN', NULL, 'SMS_TEST', 'lg-verify-child-2', '2026-06-01 10:00:00'),
(4, 2, (SELECT `id` FROM `T_MBR_AGRMT_M` WHERE `code` = 'PRIVACY' AND `version` = '1.0'),
 'AGREED', 'LEGAL_GUARDIAN', NULL, 'SMS_TEST', 'lg-verify-child-2', '2026-06-01 10:00:00'),
(5, 3, (SELECT `id` FROM `T_MBR_AGRMT_M` WHERE `code` = 'SERVICE_TERMS' AND `version` = '1.0'),
 'AGREED', 'LEGAL_GUARDIAN', NULL, 'SMS_TEST', 'lg-verify-child-3', '2026-06-01 10:05:00'),
(6, 3, (SELECT `id` FROM `T_MBR_AGRMT_M` WHERE `code` = 'PRIVACY' AND `version` = '1.0'),
 'AGREED', 'LEGAL_GUARDIAN', NULL, 'SMS_TEST', 'lg-verify-child-3', '2026-06-01 10:05:00');

-- =====================================================================
-- T_MCC_CTGR_C / T_MCC_CODE_C 시드 데이터
-- 자녀 지갑 서비스 업종 카테고리 21개 + 업종코드(6자리) 1,611건
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) T_MCC_CTGR_C (업종 카테고리) : 21건
--    id는 AUTO_INCREMENT에 위임. 아래 순서대로 삽입되어 id 1~21이 순서대로 채번됨.
-- ---------------------------------------------------------------------
INSERT INTO `T_MCC_CTGR_C` (`name`, `default_policy`) VALUES
  ('편의점', 'ALLOW'),
  ('카페·디저트', 'ALLOW'),
  ('문구·도서·완구', 'ALLOW'),
  ('게임', 'WATCH'),
  ('PC방·노래방', 'WATCH'),
  ('패션·뷰티', 'ALLOW'),
  ('대중교통', 'ALLOW'),
  ('통신', 'ALLOW'),
  ('영화·공연·테마파크', 'WATCH'),
  ('온라인쇼핑', 'WATCH'),
  ('학원·교육', 'ALLOW'),
  ('유흥·성인업소', 'BLOCK'),
  ('사행성·도박', 'BLOCK'),
  ('성인숙박업', 'BLOCK'),
  ('일반숙박업', 'WATCH'),
  ('생활용품·잡화', 'ALLOW'),
  ('외식·숙박', 'ALLOW'),
  ('의료·건강', 'ALLOW'),
  ('문화·여가', 'WATCH'),
  ('생활서비스', 'WATCH'),
  ('기타', 'ALLOW');

-- ---------------------------------------------------------------------
-- 2) T_MCC_CODE_C (업종코드) : 1,611건
--    merchant_category_id는 T_MCC_CTGR_C.name으로 JOIN하여 자동 매핑
--    (AUTO_INCREMENT id를 하드코딩하지 않기 위함)
-- ---------------------------------------------------------------------
-- batch 1 ~ 300
INSERT INTO `T_MCC_CODE_C` (`id`, `merchant_category_id`, `name`)
SELECT src.code, ctgr.id, src.name
FROM (
  SELECT '011000' AS code, '곡물 및 기타 식량작물 재배업' AS name, '기타' AS category
  UNION ALL
  SELECT '011001' AS code, '채소작물 재배업' AS name, '기타' AS category
  UNION ALL
  SELECT '011002' AS code, '화훼작물 재배업' AS name, '기타' AS category
  UNION ALL
  SELECT '011003' AS code, '종자 및 묘목 생산업' AS name, '기타' AS category
  UNION ALL
  SELECT '011004' AS code, '과실작물 재배업' AS name, '기타' AS category
  UNION ALL
  SELECT '011005' AS code, '음료용 및 향신용 작물 재배업' AS name, '기타' AS category
  UNION ALL
  SELECT '011006' AS code, '기타 작물 재배업' AS name, '기타' AS category
  UNION ALL
  SELECT '011007' AS code, '콩나물 재배업' AS name, '기타' AS category
  UNION ALL
  SELECT '011008' AS code, '채소, 화훼 및 과실작물 시설 재배업' AS name, '기타' AS category
  UNION ALL
  SELECT '011009' AS code, '기타 시설작물 재배업' AS name, '기타' AS category
  UNION ALL
  SELECT '012101' AS code, '육우 사육업' AS name, '기타' AS category
  UNION ALL
  SELECT '012102' AS code, '말 및 양 사육업' AS name, '기타' AS category
  UNION ALL
  SELECT '012103' AS code, '그 외 기타 축산업(사슴, 토끼, 개)' AS name, '기타' AS category
  UNION ALL
  SELECT '012104' AS code, '젖소 사육업' AS name, '기타' AS category
  UNION ALL
  SELECT '012201' AS code, '양돈업' AS name, '기타' AS category
  UNION ALL
  SELECT '012202' AS code, '양계업' AS name, '기타' AS category
  UNION ALL
  SELECT '012203' AS code, '기타 가금류 및 조류 사육업' AS name, '기타' AS category
  UNION ALL
  SELECT '012204' AS code, '그 외 기타 축산업' AS name, '기타' AS category
  UNION ALL
  SELECT '014300' AS code, '축산 관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '014301' AS code, '작물재배 및 축산 복합농업' AS name, '기타' AS category
  UNION ALL
  SELECT '014302' AS code, '작물재배 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '014303' AS code, '농산물 건조, 선별 및 기타 수확 후 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '015000' AS code, '수렵 및 관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '020100' AS code, '임업용 종묘 생산업' AS name, '기타' AS category
  UNION ALL
  SELECT '020101' AS code, '육림업' AS name, '기타' AS category
  UNION ALL
  SELECT '020102' AS code, '임산물 채취업' AS name, '기타' AS category
  UNION ALL
  SELECT '020200' AS code, '벌목업' AS name, '기타' AS category
  UNION ALL
  SELECT '020300' AS code, '임업 관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '051102' AS code, '연근해 어업' AS name, '기타' AS category
  UNION ALL
  SELECT '051104' AS code, '원양 어업' AS name, '기타' AS category
  UNION ALL
  SELECT '051200' AS code, '내수면 어업' AS name, '기타' AS category
  UNION ALL
  SELECT '052101' AS code, '해수면 양식 어업' AS name, '기타' AS category
  UNION ALL
  SELECT '052103' AS code, '내수면 양식 어업' AS name, '기타' AS category
  UNION ALL
  SELECT '052104' AS code, '수산물 부화 및 수산 종자 생산업' AS name, '기타' AS category
  UNION ALL
  SELECT '052200' AS code, '어업 관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '101000' AS code, '석탄 광업' AS name, '기타' AS category
  UNION ALL
  SELECT '101001' AS code, '그 외 기타 비금속광물 광업' AS name, '기타' AS category
  UNION ALL
  SELECT '131000' AS code, '철 광업' AS name, '기타' AS category
  UNION ALL
  SELECT '131003' AS code, '비철금속 광업' AS name, '기타' AS category
  UNION ALL
  SELECT '132002' AS code, '비철금속 광업' AS name, '기타' AS category
  UNION ALL
  SELECT '132003' AS code, '비철금속 광업' AS name, '기타' AS category
  UNION ALL
  SELECT '141001' AS code, '건설용 석재 채굴 및 쇄석 생산업' AS name, '기타' AS category
  UNION ALL
  SELECT '141002' AS code, '석회석 및 점토 광업' AS name, '기타' AS category
  UNION ALL
  SELECT '141003' AS code, '건설용 석재 채굴 및 쇄석 생산업' AS name, '기타' AS category
  UNION ALL
  SELECT '141004' AS code, '모래 및 자갈 채취업' AS name, '기타' AS category
  UNION ALL
  SELECT '141006' AS code, '모래 및 자갈 채취업' AS name, '기타' AS category
  UNION ALL
  SELECT '142101' AS code, '그 외 기타 비금속광물 광업' AS name, '기타' AS category
  UNION ALL
  SELECT '142102' AS code, '화학용 및 비료 원료용 광물 광업' AS name, '기타' AS category
  UNION ALL
  SELECT '142104' AS code, '화학용 및 비료 원료용 광물 광업' AS name, '기타' AS category
  UNION ALL
  SELECT '142200' AS code, '천일염 생산 및 암염 채취업' AS name, '기타' AS category
  UNION ALL
  SELECT '142902' AS code, '그 외 기타 비금속광물 광업' AS name, '기타' AS category
  UNION ALL
  SELECT '142909' AS code, '원유 및 천연가스 채굴업' AS name, '기타' AS category
  UNION ALL
  SELECT '142910' AS code, '광업 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '143101' AS code, '비철금속 광업(조광권자)' AS name, '기타' AS category
  UNION ALL
  SELECT '143103' AS code, '철 광업(조광권자)' AS name, '기타' AS category
  UNION ALL
  SELECT '143106' AS code, '석탄 광업(조광권자)' AS name, '기타' AS category
  UNION ALL
  SELECT '143107' AS code, '석회석 및 점토 광업' AS name, '기타' AS category
  UNION ALL
  SELECT '143200' AS code, '무형 재산권 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '151101' AS code, '육류 도축업(가금류 제외)' AS name, '기타' AS category
  UNION ALL
  SELECT '151102' AS code, '가금류 가공 및 저장 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '151103' AS code, '가금류 도축업' AS name, '기타' AS category
  UNION ALL
  SELECT '151104' AS code, '육류 포장육 및 냉동육 가공업(가금류 제외)' AS name, '기타' AS category
  UNION ALL
  SELECT '151105' AS code, '육류 기타 가공 및 저장 처리업(가금류 제외)' AS name, '기타' AS category
  UNION ALL
  SELECT '151200' AS code, '수산동물 건조 및 염장품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '151201' AS code, '수산동물 훈제, 조리 및 유사 조제식품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '151202' AS code, '수산동물 냉동품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '151203' AS code, '기타 수산동물 가공 및 저장 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '151301' AS code, '김치류 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '151302' AS code, '기타 과실ㆍ채소 가공 및 저장 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '151304' AS code, '과실 및 그 외 채소 절임식품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '151400' AS code, '식물성 유지 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '151401' AS code, '동물성 유지 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '151402' AS code, '식용 정제유 및 가공유 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '152001' AS code, '액상 시유 및 기타 낙농제품 제조업(분유 및 조제분유)' AS name, '기타' AS category
  UNION ALL
  SELECT '152002' AS code, '액상 시유 및 기타 낙농제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '152003' AS code, '아이스크림 및 기타 식용 빙과류 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '153101' AS code, '기타 곡물 가공품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '153102' AS code, '곡물 도정업' AS name, '기타' AS category
  UNION ALL
  SELECT '153103' AS code, '곡물 제분업' AS name, '기타' AS category
  UNION ALL
  SELECT '153104' AS code, '단미사료 및 기타 사료 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '153105' AS code, '전분제품 및 당류 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '153106' AS code, '곡물 혼합 분말 및 반죽 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '153107' AS code, '도시락류 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '153202' AS code, '전분제품 및 당류 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '153300' AS code, '배합 사료 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '153301' AS code, '단미사료 및 기타 사료 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154101' AS code, '떡류 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154102' AS code, '과자류 및 코코아 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154103' AS code, '기타 식사용 가공처리 조리식품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154104' AS code, '빵류 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154200' AS code, '설탕 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154400' AS code, '면류, 마카로니 및 유사 식품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154501' AS code, '천연 및 혼합 조제 조미료 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154502' AS code, '장류 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154503' AS code, '식초, 발효 및 화학 조미료 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154509' AS code, '기타 식품 첨가물 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154801' AS code, '곡물 도정업' AS name, '기타' AS category
  UNION ALL
  SELECT '154802' AS code, '곡물 제분업' AS name, '기타' AS category
  UNION ALL
  SELECT '154803' AS code, '육류 도축업(가금류 제외)' AS name, '기타' AS category
  UNION ALL
  SELECT '154804' AS code, '가금류 가공 및 저장 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '154805' AS code, '수산동물 건조 및 염장품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154806' AS code, '곡물 제분업' AS name, '기타' AS category
  UNION ALL
  SELECT '154901' AS code, '커피 가공업' AS name, '기타' AS category
  UNION ALL
  SELECT '154902' AS code, '두부 및 유사 식품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154903' AS code, '건강 보조용 액화식품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154904' AS code, '수산식물 가공 및 저장 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '154905' AS code, '차류 가공업' AS name, '기타' AS category
  UNION ALL
  SELECT '154906' AS code, '인삼식품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154907' AS code, '수프 및 균질화식품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154908' AS code, '건강 기능식품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '154909' AS code, '그 외 기타 식료품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '155101' AS code, '주정 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '155102' AS code, '소주 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '155103' AS code, '기타 증류주 및 합성주 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '155201' AS code, '탁주 및 약주 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '155202' AS code, '탁주 및 약주 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '155203' AS code, '기타 발효주 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '155300' AS code, '맥아 및 맥주 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '155401' AS code, '기타 비알코올 음료 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '155402' AS code, '기타 비알코올 음료 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '155403' AS code, '생수 생산업' AS name, '기타' AS category
  UNION ALL
  SELECT '155404' AS code, '얼음 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '160000' AS code, '담배제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '171101' AS code, '화학섬유 방적업' AS name, '기타' AS category
  UNION ALL
  SELECT '171102' AS code, '면 방적업' AS name, '기타' AS category
  UNION ALL
  SELECT '171104' AS code, '모 방적업' AS name, '기타' AS category
  UNION ALL
  SELECT '171105' AS code, '연사 및 가공사 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '171106' AS code, '화학섬유 방적업' AS name, '기타' AS category
  UNION ALL
  SELECT '171107' AS code, '화학섬유 방적업' AS name, '기타' AS category
  UNION ALL
  SELECT '171108' AS code, '화학섬유 방적업' AS name, '기타' AS category
  UNION ALL
  SELECT '171109' AS code, '화학섬유직물 직조업' AS name, '기타' AS category
  UNION ALL
  SELECT '171110' AS code, '기타 방적업' AS name, '기타' AS category
  UNION ALL
  SELECT '171112' AS code, '모직물 직조업' AS name, '기타' AS category
  UNION ALL
  SELECT '171114' AS code, '기타 방적업' AS name, '기타' AS category
  UNION ALL
  SELECT '171115' AS code, '면직물 직조업' AS name, '기타' AS category
  UNION ALL
  SELECT '171116' AS code, '특수직물 및 기타 직물 직조업' AS name, '기타' AS category
  UNION ALL
  SELECT '171200' AS code, '직물, 편조 원단 및 의복류 염색 가공업' AS name, '기타' AS category
  UNION ALL
  SELECT '171201' AS code, '솜 및 실 염색 가공업' AS name, '기타' AS category
  UNION ALL
  SELECT '171202' AS code, '날염 가공업' AS name, '기타' AS category
  UNION ALL
  SELECT '171203' AS code, '섬유제품 기타 정리 및 마무리 가공업' AS name, '기타' AS category
  UNION ALL
  SELECT '172101' AS code, '침구 및 관련제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172102' AS code, '자수제품 및 자수용 재료 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172103' AS code, '천막, 텐트 및 유사 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172106' AS code, '커튼 및 유사 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172107' AS code, '직물포대 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172108' AS code, '자동차용 신품 의자 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172109' AS code, '기타 직물제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172111' AS code, '기타 직물제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172200' AS code, '카펫, 마루덮개 및 유사 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172300' AS code, '어망 및 기타 끈 가공품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172301' AS code, '끈 및 로프 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172901' AS code, '세폭직물 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172902' AS code, '그 외 기타 분류 안된 섬유제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172903' AS code, '부직포 및 펠트 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172904' AS code, '특수사 및 코드직물 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '172905' AS code, '표면처리 및 적층 직물 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '173001' AS code, '편조의복 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '173002' AS code, '편조 원단 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '173004' AS code, '스타킹 및 기타양말 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '173006' AS code, '기타 편조 의복 액세서리 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '173009' AS code, '기타 편조 의복 액세서리 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181101' AS code, '남자용 겉옷 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181103' AS code, '한복 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181105' AS code, '여자용 겉옷 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181109' AS code, '셔츠 및 블라우스 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181110' AS code, '근무복, 작업복 및 유사 의복 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181201' AS code, '가죽의복 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181202' AS code, '모자 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181203' AS code, '속옷 및 잠옷 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181204' AS code, '그 외 기타 의복 액세서리 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181205' AS code, '남자용 겉옷 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181206' AS code, '여자용 겉옷 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181207' AS code, '근무복, 작업복 및 유사 의복 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181208' AS code, '유아용 의복 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181209' AS code, '한복 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '181211' AS code, '그 외 기타 봉제의복 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '182001' AS code, '모피제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '191100' AS code, '모피 및 가죽 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '191200' AS code, '가방 및 기타 보호용 케이스 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '191201' AS code, '기타 가죽제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '191202' AS code, '핸드백 및 지갑 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '192001' AS code, '구두류 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '192004' AS code, '기타 신발 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '192005' AS code, '신발 부분품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '193001' AS code, '연사 및 가공사 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '193009' AS code, '솜 및 실 염색 가공업' AS name, '기타' AS category
  UNION ALL
  SELECT '201001' AS code, '일반 제재업' AS name, '기타' AS category
  UNION ALL
  SELECT '201002' AS code, '목재 보존, 방부처리, 도장 및 유사 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '201009' AS code, '표면 가공목재 및 특정 목적용 제재목 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '202101' AS code, '강화 및 재생 목재 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '202102' AS code, '박판, 합판 및 유사 적층판 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '202200' AS code, '기타 건축용 나무제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '202201' AS code, '목재 포장용 상자, 드럼 및 유사 용기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '202202' AS code, '목재 문 및 관련제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '202203' AS code, '목재 깔판류 및 기타 적재판 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '202901' AS code, '목재 도구 및 주방용 나무제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '202902' AS code, '장식용 목제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '202903' AS code, '코르크 및 조물 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '202904' AS code, '장식용 목제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '202905' AS code, '그 외 기타 나무제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '202906' AS code, '코르크 및 조물 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '202907' AS code, '코르크 및 조물 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210101' AS code, '신문용지 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210102' AS code, '적층, 합성 및 특수 표면처리 종이 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210104' AS code, '기타 종이 및 판지 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210106' AS code, '펄프 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210107' AS code, '인쇄용 및 필기용 원지 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210108' AS code, '위생용 원지 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210111' AS code, '크라프트지 및 상자용 판지 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210112' AS code, '기타 종이 및 판지 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210200' AS code, '골판지 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210201' AS code, '판지 상자 및 용기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210202' AS code, '골판지 상자 및 가공제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210203' AS code, '종이 포대 및 가방 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210204' AS code, '식품 위생용 종이 상자 및 용기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210205' AS code, '기타 종이 상자 및 용기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210206' AS code, '그 외 기타 종이 및 판지 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210901' AS code, '문구용 종이제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210902' AS code, '벽지 및 장판지 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210903' AS code, '위생용 종이제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '210905' AS code, '그 외 기타 종이 및 판지 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '221100' AS code, '일반 서적 출판업' AS name, '기타' AS category
  UNION ALL
  SELECT '221103' AS code, '교과서 및 학습 서적 출판업' AS name, '기타' AS category
  UNION ALL
  SELECT '221104' AS code, '만화 출판업' AS name, '기타' AS category
  UNION ALL
  SELECT '221200' AS code, '잡지 및 정기 간행물 발행업' AS name, '기타' AS category
  UNION ALL
  SELECT '221201' AS code, '신문 발행업' AS name, '기타' AS category
  UNION ALL
  SELECT '221202' AS code, '정기 광고 간행물 발행업' AS name, '기타' AS category
  UNION ALL
  SELECT '221300' AS code, '음악 및 기타 오디오물 출판업' AS name, '기타' AS category
  UNION ALL
  SELECT '221900' AS code, '기타 인쇄물 출판업' AS name, '기타' AS category
  UNION ALL
  SELECT '222101' AS code, '경 인쇄업' AS name, '기타' AS category
  UNION ALL
  SELECT '222102' AS code, '스크린 인쇄업' AS name, '기타' AS category
  UNION ALL
  SELECT '222103' AS code, '경 인쇄업' AS name, '기타' AS category
  UNION ALL
  SELECT '222104' AS code, '오프셋 인쇄업' AS name, '기타' AS category
  UNION ALL
  SELECT '222105' AS code, '기타 인쇄업' AS name, '기타' AS category
  UNION ALL
  SELECT '222201' AS code, '제책업' AS name, '기타' AS category
  UNION ALL
  SELECT '222202' AS code, '기타 인쇄관련 산업' AS name, '기타' AS category
  UNION ALL
  SELECT '222203' AS code, '제판 및 조판업' AS name, '기타' AS category
  UNION ALL
  SELECT '223001' AS code, '기록매체 복제업' AS name, '기타' AS category
  UNION ALL
  SELECT '231001' AS code, '코크스 및 관련제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '231002' AS code, '연탄 및 기타 석탄 가공품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '232100' AS code, '원유 정제처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '232101' AS code, '기타 기초 무기화학 물질 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '232200' AS code, '윤활유 및 그리스 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '232201' AS code, '기타 석유 정제물 재처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '241101' AS code, '산업용 가스 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '241102' AS code, '기타 기초 무기화학 물질 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '241105' AS code, '기타 기초 무기화학 물질 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '241106' AS code, '석탄화학계 화합물 및 기타 기초 유기화학 물질 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '241109' AS code, '무기 안료용 금속 산화물 및 관련 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '241110' AS code, '석유화학계 기초 화학 물질 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '241114' AS code, '천연수지 및 나무 화학 물질 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '241115' AS code, '염료, 조제 무기 안료, 유연제 및 기타 착색제 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '241200' AS code, '복합비료 및 기타 화학비료 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '241201' AS code, '질소 화합물, 질소ㆍ인산 및 칼리질 화학비료 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '241202' AS code, '유기질 비료 및 상토 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '241301' AS code, '합성고무 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '241302' AS code, '합성수지 및 기타 플라스틱 물질 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '241303' AS code, '혼성 및 재생 플라스틱 소재 물질 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242101' AS code, '화학 살균ㆍ살충제 및 농업용 약제 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242102' AS code, '화학 살균ㆍ살충제 및 농업용 약제 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242103' AS code, '생물 살균ㆍ살충제 및 식물보호제 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242104' AS code, '생물 살균ㆍ살충제 및 식물보호제 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242201' AS code, '인쇄 잉크 및 회화용 물감 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242202' AS code, '인쇄 잉크 및 회화용 물감 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242203' AS code, '일반용 도료 및 관련제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242204' AS code, '일반용 도료 및 관련제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242205' AS code, '요업용 도포제 및 관련제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242301' AS code, '완제 의약품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242302' AS code, '한의약품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242303' AS code, '의약용 화합물 및 항생물질 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242304' AS code, '동물용 의약품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242305' AS code, '생물학적 제제 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242309' AS code, '의료용품 및 기타 의약 관련제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242401' AS code, '치약, 비누 및 기타 세제 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242402' AS code, '치약, 비누 및 기타 세제 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242403' AS code, '화장품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242404' AS code, '계면활성제 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242405' AS code, '표면 광택제 및 실내 가향제 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242406' AS code, '표면 광택제 및 실내 가향제 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242901' AS code, '접착제 및 젤라틴 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242902' AS code, '마그네틱 및 광학 매체 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242903' AS code, '그 외 기타 분류 안된 화학제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242904' AS code, '가공 및 정제염 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242905' AS code, '감광 재료 및 관련 화학제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242906' AS code, '화약 및 불꽃제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242907' AS code, '그 외 기타 분류 안된 화학제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '242908' AS code, '바이오 연료 및 혼합물 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '243000' AS code, '합성섬유 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '243001' AS code, '재생 섬유 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '251101' AS code, '타이어 및 튜브 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '251102' AS code, '타이어 재생업' AS name, '기타' AS category
  UNION ALL
  SELECT '251901' AS code, '산업용 그 외 비경화 고무제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '251902' AS code, '그 외 기타 고무제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '251903' AS code, '고무 패킹류 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '251904' AS code, '고무 의류 및 기타 위생용 비경화 고무제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252101' AS code, '플라스틱 선, 봉, 관 및 호스 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252102' AS code, '플라스틱 필름 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252103' AS code, '플라스틱 합성피혁 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252105' AS code, '플라스틱 필름 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252106' AS code, '플라스틱 시트 및 판 제조업' AS name, '기타' AS category
) AS src
JOIN `T_MCC_CTGR_C` ctgr ON ctgr.`name` = src.category;

-- batch 301 ~ 600
INSERT INTO `T_MCC_CODE_C` (`id`, `merchant_category_id`, `name`)
SELECT src.code, ctgr.id, src.name
FROM (
  SELECT '252200' AS code, '폴리스티렌 발포 성형제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252201' AS code, '기타 플라스틱 발포 성형제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252301' AS code, '설치용 및 위생용 플라스틱제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252303' AS code, '벽 및 바닥 피복용 플라스틱 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252400' AS code, '플라스틱 창호 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252401' AS code, '기타 건축용 플라스틱 조립제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252402' AS code, '플라스틱 포대, 봉투 및 유사제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252403' AS code, '플라스틱 접착처리 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252404' AS code, '플라스틱 적층, 도포 및 기타 표면처리 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252901' AS code, '포장용 플라스틱 성형용기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252902' AS code, '그 외 기타 플라스틱 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252903' AS code, '운송장비 조립용 플라스틱제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252904' AS code, '기타 기계ㆍ장비 조립용 플라스틱제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '252909' AS code, '그 외 기타 플라스틱 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '261001' AS code, '판유리 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '261002' AS code, '가정용 유리제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '261004' AS code, '안전유리 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '261005' AS code, '기타 판유리 가공품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '261006' AS code, '1차 유리제품, 유리섬유 및 광학용 유리 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '261007' AS code, '디스플레이 장치용 유리 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '261008' AS code, '기타 산업용 유리제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '261009' AS code, '포장용 유리용기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '261011' AS code, '그 외 기타 유리제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269100' AS code, '가정용 및 장식용 도자기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269101' AS code, '위생용 및 산업용 도자기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269102' AS code, '기타 일반 도자기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269200' AS code, '정형 내화 요업제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269201' AS code, '부정형 내화 요업제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269300' AS code, '타일 및 유사 비내화 요업제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269301' AS code, '점토 벽돌, 블록 및 유사 비내화 요업제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269302' AS code, '기타 건축용 비내화 요업제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269401' AS code, '시멘트 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269402' AS code, '석회 및 플라스터 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269403' AS code, '석회 및 플라스터 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269501' AS code, '레미콘 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269502' AS code, '플라스터 혼합제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269503' AS code, '콘크리트 관 및 기타 구조용 콘크리트제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269504' AS code, '콘크리트 타일, 기와, 벽돌 및 블록 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269505' AS code, '비내화 모르타르 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269506' AS code, '그 외 기타 콘크리트 제품 및 유사 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269507' AS code, '콘크리트 타일, 기와, 벽돌 및 블록 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269508' AS code, '콘크리트 관 및 기타 구조용 콘크리트제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269509' AS code, '그 외 기타 콘크리트 제품 및 유사 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269601' AS code, '건설용 석제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269603' AS code, '기타 석제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269901' AS code, '암면 및 유사 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269902' AS code, '연마재 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269903' AS code, '아스팔트 콘크리트 및 혼합제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269904' AS code, '비금속광물 분쇄물 생산업' AS name, '기타' AS category
  UNION ALL
  SELECT '269905' AS code, '탄소섬유 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269906' AS code, '그 외 기타 분류 안된 비금속 광물제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269907' AS code, '연마재 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '269908' AS code, '그 외 기타 분류 안된 비금속 광물제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '271101' AS code, '제철업' AS name, '기타' AS category
  UNION ALL
  SELECT '271102' AS code, '제강업' AS name, '기타' AS category
  UNION ALL
  SELECT '271103' AS code, '합금철 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '271104' AS code, '기타 제철 및 제강업' AS name, '기타' AS category
  UNION ALL
  SELECT '271201' AS code, '그 외 기타 1차 철강 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '271202' AS code, '주철관 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '271204' AS code, '열간 압연 및 압출제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '271205' AS code, '냉간 압연 및 압출 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '271206' AS code, '철강선 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '271207' AS code, '강관 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '271208' AS code, '강관 가공품 및 관 연결구류 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '271901' AS code, '도금, 착색 및 기타 표면 처리 강재 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '271902' AS code, '그 외 기타 1차 철강 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '272100' AS code, '기타 비철금속 제련, 정련 및 합금 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '272101' AS code, '동 제련, 정련 및 합금 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '272102' AS code, '알루미늄 제련, 정련 및 합금 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '272103' AS code, '연 및 아연 제련, 정련 및 합금 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '272200' AS code, '기타 비철금속 제련, 정련 및 합금 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '272201' AS code, '동 제련, 정련 및 합금 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '272202' AS code, '알루미늄 제련, 정련 및 합금 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '272203' AS code, '연 및 아연 제련, 정련 및 합금 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '272300' AS code, '알루미늄 압연, 압출 및 연신제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '272301' AS code, '동 압연, 압출 및 연신제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '272302' AS code, '기타 비철금속 압연, 압출 및 연신제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '272900' AS code, '기타 1차 비철금속 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '273101' AS code, '선철 주물 주조업' AS name, '기타' AS category
  UNION ALL
  SELECT '273102' AS code, '강 주물 주조업' AS name, '기타' AS category
  UNION ALL
  SELECT '273200' AS code, '알루미늄 주물 주조업' AS name, '기타' AS category
  UNION ALL
  SELECT '273201' AS code, '동 주물 주조업' AS name, '기타' AS category
  UNION ALL
  SELECT '273202' AS code, '기타 비철금속 주조업' AS name, '기타' AS category
  UNION ALL
  SELECT '281100' AS code, '금속 문, 창, 셔터 및 관련제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '281101' AS code, '그 외 기타 분류 안된 금속 가공제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '281102' AS code, '탭, 밸브 및 유사 장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '281103' AS code, '구조용 금속 판제품 및 공작물 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '281104' AS code, '육상 금속 골조 구조재 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '281105' AS code, '수상 금속 골조 구조재 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '281106' AS code, '기타 구조용 금속제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '281201' AS code, '산업용 난방보일러 및 방열기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '281202' AS code, '산업용 난방보일러 및 방열기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '281204' AS code, '금속 탱크 및 저장 용기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '281205' AS code, '압축 및 액화 가스 용기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '281300' AS code, '핵반응기 및 증기보일러 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289101' AS code, '분말 야금제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289102' AS code, '금속 단조제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289103' AS code, '자동차용 금속 압형제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289104' AS code, '그 외 금속 압형제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289201' AS code, '그 외 기타 금속 가공업' AS name, '기타' AS category
  UNION ALL
  SELECT '289202' AS code, '도장 및 기타 피막 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '289205' AS code, '절삭 가공 및 유사 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '289206' AS code, '금속 열처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '289207' AS code, '도금업' AS name, '기타' AS category
  UNION ALL
  SELECT '289208' AS code, '그 외 기타 금속 가공업' AS name, '기타' AS category
  UNION ALL
  SELECT '289301' AS code, '비동력식 수공구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289302' AS code, '일반 철물 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289303' AS code, '톱 및 호환성 공구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289304' AS code, '날붙이 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289305' AS code, '일반 철물 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289306' AS code, '비동력식 수공구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289901' AS code, '금속선 가공제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289902' AS code, '금속 캔 및 기타 포장용기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289903' AS code, '수동식 식품 가공 기기 및 금속 주방용기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289908' AS code, '볼트 및 너트류 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289910' AS code, '그 외 금속 파스너 및 나사제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289913' AS code, '금속 스프링 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289914' AS code, '피복 및 충전 용접봉 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289915' AS code, '그 외 기타 분류 안된 금속 가공제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289916' AS code, '금속 위생용품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289917' AS code, '금속 표시판 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '289918' AS code, '그 외 기타 분류 안된 금속 가공제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291100' AS code, '내연기관 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291101' AS code, '기타 기관 및 터빈 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291200' AS code, '유압 기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291201' AS code, '액체 펌프 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291202' AS code, '기체 펌프 및 압축기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291300' AS code, '기어 및 동력전달장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291301' AS code, '구름베어링 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291401' AS code, '산업용 오븐, 노 및 노용 버너 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291501' AS code, '산업용 트럭 및 적재기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291502' AS code, '기타 물품 취급장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291503' AS code, '승강기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291504' AS code, '컨베이어 장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291901' AS code, '일반 저울 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291902' AS code, '산업용 냉장 및 냉동장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291903' AS code, '공기 조화장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291904' AS code, '산업용 송풍기 및 배기장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291905' AS code, '기체 여과기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291906' AS code, '액체 여과기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291907' AS code, '증류기, 열 교환기 및 가스 발생기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291908' AS code, '용기 세척, 포장 및 충전기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291909' AS code, '분사기 및 소화기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '291911' AS code, '그 외 기타 일반 목적용 기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292100' AS code, '농업 및 임업용 기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292201' AS code, '동력식 수지 공구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292202' AS code, '금속 절삭기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292203' AS code, '전자 응용 절삭기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292204' AS code, '기타 가공 공작기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292205' AS code, '디지털 적층 성형기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292206' AS code, '금속 성형기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292300' AS code, '금속 주조 및 기타 야금용 기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292400' AS code, '건설 및 채광용 기계장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292401' AS code, '광물 처리 및 취급장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292500' AS code, '음ㆍ식료품 및 담배 가공기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292600' AS code, '기타 섬유, 의복 및 가죽 가공기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292601' AS code, '산업용 섬유 세척, 염색, 정리 및 가공 기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292700' AS code, '무기 및 총포탄 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292901' AS code, '산업용 로봇 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292902' AS code, '인쇄 및 제책용 기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292903' AS code, '주형 및 금형 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292905' AS code, '펄프 및 종이 가공용 기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292906' AS code, '고무, 화학섬유 및 플라스틱 성형기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292907' AS code, '그 외 기타 특수 목적용 기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292908' AS code, '반도체 제조용 기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '292909' AS code, '디스플레이 제조용 기계 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '293001' AS code, '주방용 전기 기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '293002' AS code, '기타 가정용 전기 기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '293004' AS code, '가정용 비전기식 조리 및 난방 기구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '293005' AS code, '가정용 전기 난방기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '300100' AS code, '컴퓨터 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '300101' AS code, '기타 주변 기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '300102' AS code, '기억 장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '300103' AS code, '컴퓨터 모니터 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '300104' AS code, '컴퓨터 프린터 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '300201' AS code, '사무용 기계 및 장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '300202' AS code, '사무용 기계 및 장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '311001' AS code, '전자코일, 변성기 및 기타 전자 유도자 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '311002' AS code, '전동기 및 발전기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '311003' AS code, '변압기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '311004' AS code, '방전 램프용 안정기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '311005' AS code, '에너지 저장장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '311006' AS code, '기타 전기 변환장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '312000' AS code, '배전반 및 전기 자동제어반 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '312001' AS code, '전기회로 개폐, 보호 장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '312002' AS code, '전기회로 접속장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '313001' AS code, '기타 절연선 및 케이블 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '313002' AS code, '절연 코드세트 및 기타 도체 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '313003' AS code, '광섬유 케이블 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '314000' AS code, '축전지 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '314001' AS code, '일차전지 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '315001' AS code, '일반용 전기 조명장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '315002' AS code, '전시 및 광고용 조명장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '315003' AS code, '전구 및 램프 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '315005' AS code, '기타 조명장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '319001' AS code, '운송장비용 조명장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '319002' AS code, '그 외 기타 전기장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '319003' AS code, '전기 경보 및 신호장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '319004' AS code, '전기용 탄소제품 및 절연제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '319005' AS code, '교통 신호장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321000' AS code, '기타 반도체 소자 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321001' AS code, '그 외 기타 전자 부품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321002' AS code, '메모리용 전자집적회로 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321003' AS code, '비메모리용 및 기타 전자집적회로 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321004' AS code, '발광 다이오드 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321005' AS code, '액정 표시장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321006' AS code, '유기 발광 표시장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321007' AS code, '기타 표시장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321008' AS code, '인쇄회로기판용 적층판 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321009' AS code, '경성 인쇄회로기판 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321011' AS code, '연성 및 기타 인쇄회로기판 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321012' AS code, '전자 부품 실장기판 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321013' AS code, '전자 축전기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321014' AS code, '전자 저항기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321015' AS code, '전자카드 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '321016' AS code, '전자 감지장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '322002' AS code, '유선 통신장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '322003' AS code, '방송장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '322004' AS code, '이동 전화기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '322005' AS code, '기타 무선 통신장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '323001' AS code, '비디오 및 기타 영상 기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '323005' AS code, '텔레비전 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '323006' AS code, '라디오, 녹음 및 재생 기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '323007' AS code, '기타 음향기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331100' AS code, '전기식 진단 및 요법 기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331101' AS code, '정형 외과용 및 신체 보정용 기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331102' AS code, '그 외 기타 의료용 기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331103' AS code, '방사선 장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331104' AS code, '치과용 기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331105' AS code, '정형 외과용 및 신체 보정용 기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331106' AS code, '의료용 가구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331201' AS code, '물질 검사, 측정 및 분석 기구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331203' AS code, '레이더, 항행용 무선 기기 및 측량 기구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331204' AS code, '전자기 측정, 시험 및 분석 기구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331205' AS code, '속도계 및 적산계기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331206' AS code, '기기용 자동 측정 및 제어장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331207' AS code, '기타 측정, 시험, 항해, 제어 및 정밀 기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '331300' AS code, '산업 처리공정 제어장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '332000' AS code, '안경 및 안경렌즈 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '332001' AS code, '사진기, 영사기 및 관련 장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '332002' AS code, '안경 및 안경렌즈 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '332003' AS code, '광학 렌즈 및 광학 요소 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '332004' AS code, '기타 광학 기기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '333000' AS code, '시계 및 시계 부품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '341001' AS code, '화물 자동차 및 특수 목적용 자동차 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '341003' AS code, '자동차용 엔진 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '341004' AS code, '승용차 및 기타 여객용 자동차 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '342000' AS code, '트레일러 및 세미 트레일러 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '342001' AS code, '차체 및 특장차 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '342002' AS code, '자동차 구조 및 장치 변경업' AS name, '기타' AS category
  UNION ALL
  SELECT '343000' AS code, '그 외 자동차용 신품 부품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '343001' AS code, '자동차 엔진용 신품 부품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '343002' AS code, '자동차 차체용 신품 부품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '343003' AS code, '자동차용 신품 동력 전달장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '343004' AS code, '자동차용 신품 전기장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '343005' AS code, '자동차용 신품 조향장치 및 현가장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '343006' AS code, '자동차용 신품 제동장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '343007' AS code, '자동차 재제조 부품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '351101' AS code, '강선 건조업' AS name, '기타' AS category
  UNION ALL
  SELECT '351102' AS code, '기타 선박 건조업' AS name, '기타' AS category
  UNION ALL
  SELECT '351103' AS code, '선박 구성 부분품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '351104' AS code, '금속류 해체 및 선별업' AS name, '기타' AS category
  UNION ALL
  SELECT '351105' AS code, '기타 선박 건조업' AS name, '기타' AS category
  UNION ALL
  SELECT '351106' AS code, '합성수지선 건조업' AS name, '기타' AS category
  UNION ALL
  SELECT '351107' AS code, '기타 선박 건조업' AS name, '기타' AS category
  UNION ALL
  SELECT '351200' AS code, '오락 및 스포츠용 보트 건조업' AS name, '기타' AS category
  UNION ALL
  SELECT '352000' AS code, '철도 차량 부품 및 관련 장치물 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '352001' AS code, '기관차 및 기타 철도 차량 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '353000' AS code, '항공기용 부품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '353001' AS code, '유인 항공기, 항공 우주선 및 보조장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '353002' AS code, '무인 항공기 및 무인 비행장치 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '353003' AS code, '항공기용 엔진 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '359100' AS code, '모터사이클 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '359200' AS code, '자전거 및 환자용 차량 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '359201' AS code, '전투용 차량 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '359202' AS code, '그 외 기타 달리 분류되지 않은 운송장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '361001' AS code, '금속 가구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '361002' AS code, '기타 목재 가구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '361003' AS code, '그 외 기타 가구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '361005' AS code, '매트리스 및 침대 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '361006' AS code, '소파 및 기타 내장 가구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '361007' AS code, '주방용 및 음식점용 목재 가구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369101' AS code, '귀금속 및 관련제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369200' AS code, '기타 악기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369201' AS code, '건반 악기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369202' AS code, '전자 악기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369301' AS code, '체조, 육상 및 체력 단련용 장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369302' AS code, '기타 운동 및 경기용구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369305' AS code, '놀이터용 장비 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369306' AS code, '낚시 및 수렵용구 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369401' AS code, '인형 및 장난감 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369402' AS code, '인형 및 장난감 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369404' AS code, '영상게임기 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369405' AS code, '기타 오락용품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369409' AS code, '기타 오락용품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369501' AS code, '모조 귀금속 및 모조 장신용품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369502' AS code, '가발 및 유사 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369503' AS code, '간판 및 광고물 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369504' AS code, '표구 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '369505' AS code, '전시용 모형 제조업' AS name, '기타' AS category
) AS src
JOIN `T_MCC_CTGR_C` ctgr ON ctgr.`name` = src.category;

-- batch 601 ~ 900
INSERT INTO `T_MCC_CODE_C` (`id`, `merchant_category_id`, `name`)
SELECT src.code, ctgr.id, src.name
FROM (
  SELECT '369902' AS code, '사무 및 회화용품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369903' AS code, '비 및 솔 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369904' AS code, '그 외 기타 달리 분류되지 않은 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369905' AS code, '단추 및 유사 파스너 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369906' AS code, '라이터, 연소물 및 흡연용품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369907' AS code, '화약 및 불꽃제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '369908' AS code, '그 외 기타 달리 분류되지 않은 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '371000' AS code, '금속류 원료 재생업' AS name, '기타' AS category
  UNION ALL
  SELECT '371001' AS code, '금속류 해체 및 선별업' AS name, '기타' AS category
  UNION ALL
  SELECT '372000' AS code, '비금속류 원료 재생업' AS name, '기타' AS category
  UNION ALL
  SELECT '372001' AS code, '비금속류 해체 및 선별업' AS name, '기타' AS category
  UNION ALL
  SELECT '372002' AS code, '토양 및 지하수 정화업' AS name, '기타' AS category
  UNION ALL
  SELECT '372003' AS code, '기타 환경 정화 및 복원업' AS name, '기타' AS category
  UNION ALL
  SELECT '381000' AS code, '절삭 가공 및 유사 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '381003' AS code, '그 외 기타 금속 가공업' AS name, '기타' AS category
  UNION ALL
  SELECT '381004' AS code, '그 외 자동차용 신품 부품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '381005' AS code, '그 외 기타 달리 분류되지 않은 제품 제조업' AS name, '기타' AS category
  UNION ALL
  SELECT '381006' AS code, '포장 및 충전업' AS name, '기타' AS category
  UNION ALL
  SELECT '381007' AS code, '기타 소사장제' AS name, '기타' AS category
  UNION ALL
  SELECT '401000' AS code, '태양력 발전업' AS name, '기타' AS category
  UNION ALL
  SELECT '401001' AS code, '원자력 발전업' AS name, '기타' AS category
  UNION ALL
  SELECT '401002' AS code, '수력 발전업' AS name, '기타' AS category
  UNION ALL
  SELECT '401003' AS code, '화력 발전업' AS name, '기타' AS category
  UNION ALL
  SELECT '401004' AS code, '기타 발전업' AS name, '기타' AS category
  UNION ALL
  SELECT '401005' AS code, '송전 및 배전업' AS name, '기타' AS category
  UNION ALL
  SELECT '401006' AS code, '전기 판매업' AS name, '기타' AS category
  UNION ALL
  SELECT '402001' AS code, '연료용 가스 제조 및 배관공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '403000' AS code, '증기, 냉ㆍ온수 및 공기 조절 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '410000' AS code, '생활용수 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '410001' AS code, '산업용수 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '451101' AS code, '아파트 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451102' AS code, '주거용 건물 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451103' AS code, '주거용 건물 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451104' AS code, '기타 비주거용 건물 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451105' AS code, '단독 주택 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451106' AS code, '단독 주택 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451107' AS code, '기타 공동 주택 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451108' AS code, '사무ㆍ상업용 및 공공기관용 건물 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451109' AS code, '제조업 및 유사 산업용 건물 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451200' AS code, '기타 토목 시설물 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451204' AS code, '지반조성 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451205' AS code, '도로 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451206' AS code, '교량, 터널 및 철도 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451207' AS code, '항만, 수로, 댐 및 유사 구조물 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451208' AS code, '환경설비 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451300' AS code, '산업 생산시설 종합 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '451400' AS code, '조경 건설업' AS name, '기타' AS category
  UNION ALL
  SELECT '452101' AS code, '미장, 타일 및 방수 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452102' AS code, '유리 및 창호 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452103' AS code, '보링, 그라우팅 및 관정 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452104' AS code, '배관 및 냉ㆍ난방 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452105' AS code, '도배, 실내 장식 및 내장 목공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452106' AS code, '도배, 실내 장식 및 내장 목공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452107' AS code, '도배, 실내 장식 및 내장 목공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452108' AS code, '포장 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452109' AS code, '수중 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452110' AS code, '토공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452111' AS code, '시설물 유지관리 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452112' AS code, '보링, 그라우팅 및 관정 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452113' AS code, '파일공사 및 축조관련 기초 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452114' AS code, '조적 및 석공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452115' AS code, '방음, 방진 및 내화 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452116' AS code, '소방시설 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452117' AS code, '건물 및 구축물 해체 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452118' AS code, '철골 및 관련 구조물 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452119' AS code, '콘크리트 및 철근 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452120' AS code, '비계 및 형틀 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452121' AS code, '건물용 금속 공작물 설치 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452122' AS code, '철도 궤도 전문공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452123' AS code, '지붕, 내ㆍ외벽 축조 관련 전문공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452124' AS code, '건물용 기계ㆍ장비 설치 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452125' AS code, '일반 전기 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452126' AS code, '내부 전기배선 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452127' AS code, '일반 통신 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452128' AS code, '내부 통신배선 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452129' AS code, '도장 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452130' AS code, '기타 기반조성 관련 전문공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452131' AS code, '기타 옥외 시설물 축조관련 전문공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452132' AS code, '배관 및 냉ㆍ난방 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452200' AS code, '그 외 기타 건축 마무리 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '452201' AS code, '기타 건물 관련설비 설치 공사업' AS name, '기타' AS category
  UNION ALL
  SELECT '453000' AS code, '건설장비 운영업' AS name, '기타' AS category
  UNION ALL
  SELECT '501101' AS code, '자동차 신품 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '501103' AS code, '중고 자동차 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '501201' AS code, '자동차 신품 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '501202' AS code, '중고 자동차 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '501301' AS code, '자동차 신품 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '501302' AS code, '자동차 신품 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '501303' AS code, '중고 자동차 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '503001' AS code, '자동차 신품 타이어 및 튜브 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '503002' AS code, '자동차 신품 타이어 및 튜브 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '503003' AS code, '자동차 중고 부품 및 내장품 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '503004' AS code, '자동차 중고 부품 및 내장품 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '503005' AS code, '기타 자동차 신품 부품 및 내장품 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '503006' AS code, '자동차용 전용 신품 부품 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '503007' AS code, '자동차 내장용 신품 전기ㆍ전자ㆍ정밀기기 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '503008' AS code, '기타 자동차 신품 부품 및 내장품 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '503009' AS code, '자동차용 전용 신품 부품 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '503010' AS code, '자동차 내장용 신품 전기ㆍ전자ㆍ정밀기기 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '503013' AS code, '기타 자동차 신품 부품 및 내장품 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '504001' AS code, '모터사이클 및 부품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '504002' AS code, '모터사이클 및 부품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '505001' AS code, '운송장비용 주유소 운영업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '505002' AS code, '운송장비용 가스 충전업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '511111' AS code, '산업용 농ㆍ축산물, 섬유 원료 및 동물 중개업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '511112' AS code, '산업용 농ㆍ축산물, 섬유 원료 및 동물 중개업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '511113' AS code, '음ㆍ식료품 및 담배 중개업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '511114' AS code, '음ㆍ식료품 및 담배 중개업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '511115' AS code, '섬유, 의복, 신발 및 가죽제품 중개업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '511116' AS code, '섬유, 의복, 신발 및 가죽제품 중개업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '511117' AS code, '섬유, 의복, 신발 및 가죽제품 중개업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '511118' AS code, '음ㆍ식료품 및 담배 중개업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '511119' AS code, '섬유, 의복, 신발 및 가죽제품 중개업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '511121' AS code, '음ㆍ식료품 및 담배 중개업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '511122' AS code, '음ㆍ식료품 및 담배 중개업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '511123' AS code, '음ㆍ식료품 및 담배 중개업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512111' AS code, '곡물 및 유지작물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512112' AS code, '곡물 및 유지작물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512113' AS code, '종자 및 묘목 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512116' AS code, '기타 가공식품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512119' AS code, '곡물 및 유지작물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512120' AS code, '사료 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512131' AS code, '화훼류 및 식물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512132' AS code, '화훼류 및 식물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512133' AS code, '화훼류 및 식물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512135' AS code, '종자 및 묘목 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512140' AS code, '육지 동물 및 애완 동물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512190' AS code, '기타 산업용 농산물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512192' AS code, '육지 동물 및 애완 동물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512211' AS code, '과실류 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512212' AS code, '채소류, 서류 및 향신작물류 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512214' AS code, '과실류 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512215' AS code, '채소류, 서류 및 향신작물류 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512221' AS code, '기타 산업용 농산물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512222' AS code, '기타 산업용 농산물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512223' AS code, '육류 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512224' AS code, '육류 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512231' AS code, '신선, 냉동 및 기타 수산물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512232' AS code, '신선, 냉동 및 기타 수산물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512233' AS code, '건어물 및 젓갈류 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512234' AS code, '신선, 냉동 및 기타 수산물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512235' AS code, '신선, 냉동 및 기타 수산물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512236' AS code, '건어물 및 젓갈류 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512241' AS code, '빵류, 과자류, 당류, 초콜릿 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512242' AS code, '빵류, 과자류, 당류, 초콜릿 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512243' AS code, '빵류, 과자류, 당류, 초콜릿 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512244' AS code, '빵류, 과자류, 당류, 초콜릿 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512245' AS code, '낙농품 및 동ㆍ식물성 유지 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512251' AS code, '주류 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512252' AS code, '주류 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512260' AS code, '비알코올 음료 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512271' AS code, '육류 가공식품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512272' AS code, '조미료 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512273' AS code, '커피 및 차류 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512274' AS code, '기타 가공식품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512279' AS code, '기타 가공식품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512280' AS code, '기타 가공식품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512281' AS code, '기타 신선 식품 및 단순 가공 식품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512282' AS code, '낙농품 및 동ㆍ식물성 유지 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512283' AS code, '낙농품 및 동ㆍ식물성 유지 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512284' AS code, '기타 가공식품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512285' AS code, '낙농품 및 동ㆍ식물성 유지 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512286' AS code, '담배 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512287' AS code, '기타 가공식품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512288' AS code, '기타 가공식품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512289' AS code, '수산물 가공식품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512290' AS code, '낙농품 및 동ㆍ식물성 유지 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512293' AS code, '복권 발행 및 판매업' AS name, '사행성·도박' AS category
  UNION ALL
  SELECT '512294' AS code, '낙농품 및 동ㆍ식물성 유지 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '512295' AS code, '기타 가공식품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513111' AS code, '생활용 섬유 및 실 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513112' AS code, '커튼 및 침구용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513113' AS code, '커튼 및 침구용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513119' AS code, '기타 생활용 섬유 및 직물 제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513121' AS code, '남녀용 겉옷 및 셔츠 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513130' AS code, '남녀용 겉옷 및 셔츠 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513131' AS code, '유아용 의류 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513132' AS code, '속옷 및 잠옷 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513141' AS code, '신발 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513150' AS code, '가죽 및 모피 제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513191' AS code, '의복 액세서리 및 모조 장신구 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513211' AS code, '생활용 가구 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513221' AS code, '가전제품 및 부품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513230' AS code, '생활용 유리ㆍ요업ㆍ목재ㆍ금속 제품 및 날붙이 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513231' AS code, '그 외 기타 기계 및 장비 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513241' AS code, '전구, 램프 및 조명장치 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513250' AS code, '악기 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513253' AS code, '음반 및 비디오물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513261' AS code, '기타 생활용 섬유 및 직물 제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513263' AS code, '벽지 및 장판류 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513271' AS code, '생활용 유리ㆍ요업ㆍ목재ㆍ금속 제품 및 날붙이 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513272' AS code, '생활용 유리ㆍ요업ㆍ목재ㆍ금속 제품 및 날붙이 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513290' AS code, '생활용 가구 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513291' AS code, '가전제품 및 부품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513295' AS code, '생활용 유리ㆍ요업ㆍ목재ㆍ금속 제품 및 날붙이 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513296' AS code, '기타 비전기식 생활용 기기 및 기구 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513297' AS code, '자전거 및 기타 운송장비 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513298' AS code, '그 외 기타 상품 전문 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513311' AS code, '의약품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513312' AS code, '의약품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513313' AS code, '의료용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513320' AS code, '화장품 및 화장용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513330' AS code, '비누 및 세정제 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513410' AS code, '생활용 포장 및 위생용품, 봉투 및 유사 제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513414' AS code, '종이 원지, 판지, 종이상자 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513415' AS code, '그 외 기타 상품 전문 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513421' AS code, '서적, 잡지 및 기타 인쇄물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513430' AS code, '문구용품, 회화용품, 사무용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513911' AS code, '안경, 사진장비 및 광학용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513912' AS code, '안경, 사진장비 및 광학용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513913' AS code, '안경, 사진장비 및 광학용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513920' AS code, '의복 액세서리 및 모조 장신구 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513921' AS code, '장난감 및 취미, 오락용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513931' AS code, '시계 및 귀금속 제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513933' AS code, '시계 및 귀금속 제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513934' AS code, '시계 및 귀금속 제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513941' AS code, '운동 및 경기용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513942' AS code, '운동 및 경기용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513945' AS code, '장난감 및 취미, 오락용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513950' AS code, '가방 및 보호용 케이스 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513960' AS code, '자전거 및 기타 운송장비 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513991' AS code, '의복 액세서리 및 모조 장신구 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '513992' AS code, '그 외 기타 생활용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514111' AS code, '고체 연료 및 관련제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514112' AS code, '고체 연료 및 관련제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514121' AS code, '액체 연료 및 관련제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514122' AS code, '그 외 기타 상품 전문 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514130' AS code, '기체 연료 및 관련제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514210' AS code, '1차 금속제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514220' AS code, '1차 금속제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514232' AS code, '1차 금속제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514239' AS code, '1차 금속제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514240' AS code, '1차 금속제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514250' AS code, '금속광물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514291' AS code, '1차 금속제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514292' AS code, '1차 금속제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514312' AS code, '원목 및 건축관련 목제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514320' AS code, '골재, 벽돌 및 시멘트 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514321' AS code, '유리 및 창호 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514330' AS code, '골재, 벽돌 및 시멘트 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514350' AS code, '도료 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514361' AS code, '철물, 금속 파스너 및 수공구 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514366' AS code, '배관 및 냉ㆍ난방장치 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514370' AS code, '그 외 기타 건축자재 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514380' AS code, '골재, 벽돌 및 시멘트 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514392' AS code, '그 외 기타 건축자재 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514393' AS code, '그 외 기타 건축자재 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514911' AS code, '기타 화학 물질 및 화학제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514912' AS code, '기타 화학 물질 및 화학제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514916' AS code, '플라스틱 물질 및 합성고무 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514919' AS code, '기타 화학 물질 및 화학제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514920' AS code, '염료, 안료 및 관련제품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514930' AS code, '비료 및 농약 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514940' AS code, '비료 및 농약 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514950' AS code, '플라스틱 물질 및 합성고무 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514961' AS code, '방직용 섬유 및 실 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514969' AS code, '직물 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514971' AS code, '재생용 재료 수집 및 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '514990' AS code, '그 외 기타 상품 전문 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515010' AS code, '그 외 기타 기계 및 장비 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515020' AS code, '농림업용 기계 및 장비 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515030' AS code, '건설ㆍ광업용 기계 및 장비 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515031' AS code, '그 외 기타 기계 및 장비 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515040' AS code, '공작용 기계 및 장비 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515050' AS code, '컴퓨터 및 주변장치, 소프트웨어 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515051' AS code, '사무용 가구 및 기기 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515060' AS code, '통신ㆍ방송장비 및 부품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515070' AS code, '전기용 기계ㆍ장비 및 관련 기자재 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515074' AS code, '전지 및 케이블 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515080' AS code, '의료 기기 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515081' AS code, '정밀 기기 및 과학 기기 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515090' AS code, '기타 산업용 기계 및 장비 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515093' AS code, '자전거 및 기타 운송장비 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '515094' AS code, '수송용 운송장비 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '519111' AS code, '상품 종합 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '519112' AS code, '상품 종합 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '519113' AS code, '상품 종합 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '519910' AS code, '상품 종합 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '519991' AS code, '장난감 및 취미, 오락용품 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '519992' AS code, '그 외 기타 상품 전문 도매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '521100' AS code, '슈퍼마켓' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '521910' AS code, '백화점' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '521911' AS code, '기타 대형 종합 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '521912' AS code, '대형 마트' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '521913' AS code, '면세점' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '521991' AS code, '그 외 기타 종합 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '521992' AS code, '체인화 편의점' AS name, '편의점' AS category
  UNION ALL
  SELECT '522011' AS code, '곡물, 곡분 및 가축 사료 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522012' AS code, '곡물, 곡분 및 가축 사료 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522020' AS code, '육류 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522031' AS code, '신선, 냉동 및 기타 수산물 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522039' AS code, '건어물 및 젓갈류 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522040' AS code, '채소, 과실 및 뿌리작물 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522050' AS code, '채소, 과실 및 뿌리작물 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522061' AS code, '빵류, 과자류 및 당류 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522062' AS code, '빵류, 과자류 및 당류 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522063' AS code, '기타 식료품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522071' AS code, '기타 음ㆍ식료품 위주 종합 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522080' AS code, '담배 소매업' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '522081' AS code, '그 외 기타 분류 안된 가정용품 소매업' AS name, '생활용품·잡화' AS category
) AS src
JOIN `T_MCC_CTGR_C` ctgr ON ctgr.`name` = src.category;

-- batch 901 ~ 1200
INSERT INTO `T_MCC_CODE_C` (`id`, `merchant_category_id`, `name`)
SELECT src.code, ctgr.id, src.name
FROM (
  SELECT '522082' AS code, '복권 발행 및 판매업' AS name, '사행성·도박' AS category
  UNION ALL
  SELECT '522091' AS code, '건강 보조식품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522092' AS code, '그 외 기타 분류 안된 상품 전문 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522093' AS code, '음료 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522094' AS code, '음료 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522095' AS code, '음료 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522096' AS code, '기타 식료품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522098' AS code, '음료 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522099' AS code, '음료 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522101' AS code, '건강 보조식품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522105' AS code, '그 외 기타 분류 안된 상품 전문 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '522109' AS code, '조리 반찬류 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523111' AS code, '의약품 및 의료용품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523114' AS code, '의약품 및 의료용품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523116' AS code, '의약품 및 의료용품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523120' AS code, '의료용 기구 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523131' AS code, '화장품, 비누 및 방향제 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523132' AS code, '방문 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523211' AS code, '섬유 원단, 실 및 기타 섬유제품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523214' AS code, '섬유 원단, 실 및 기타 섬유제품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523221' AS code, '속옷 및 잠옷 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523223' AS code, '유아용 의류 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523231' AS code, '셔츠 및 블라우스 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523233' AS code, '남자용 겉옷 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523234' AS code, '여자용 겉옷 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523235' AS code, '한복 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523236' AS code, '가죽 및 모피 의복 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523237' AS code, '기타 의복 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523241' AS code, '신발 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523242' AS code, '신발 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523252' AS code, '의복 액세서리 및 모조 장신구 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523260' AS code, '가방 및 기타 가죽제품 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523291' AS code, '섬유 원단, 실 및 기타 섬유제품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523292' AS code, '의복 액세서리 및 모조 장신구 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523311' AS code, '가구 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523312' AS code, '가구 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523314' AS code, '가구 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523321' AS code, '가전제품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523323' AS code, '통신기기 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523330' AS code, '가전제품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523331' AS code, '가구 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523332' AS code, '주방용품 및 가정용 유리, 요업제품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523341' AS code, '악기 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523349' AS code, '악기 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523352' AS code, '전기용품 및 조명장치 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523361' AS code, '가정용 직물제품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523363' AS code, '가정용 직물제품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523370' AS code, '그 외 기타 분류 안된 가정용품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523390' AS code, '가전제품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523391' AS code, '그 외 기타 분류 안된 가정용품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523411' AS code, '철물 및 난방용구 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523412' AS code, '철물 및 난방용구 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523413' AS code, '공구 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523421' AS code, '페인트, 창호 및 기타 건설자재 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523422' AS code, '주방용품 및 가정용 유리, 요업제품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523491' AS code, '페인트, 창호 및 기타 건설자재 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523492' AS code, '페인트, 창호 및 기타 건설자재 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523494' AS code, '페인트, 창호 및 기타 건설자재 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523499' AS code, '페인트, 창호 및 기타 건설자재 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523511' AS code, '서적, 신문 및 잡지류 소매업' AS name, '문구·도서·완구' AS category
  UNION ALL
  SELECT '523520' AS code, '문구용품 및 회화용품 소매업' AS name, '문구·도서·완구' AS category
  UNION ALL
  SELECT '523521' AS code, '서적, 신문 및 잡지류 소매업' AS name, '문구·도서·완구' AS category
  UNION ALL
  SELECT '523531' AS code, '컴퓨터 및 주변장치, 소프트웨어 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523532' AS code, '사무용 기기 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523541' AS code, '안경 및 렌즈 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523550' AS code, '사진기 및 사진용품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523552' AS code, '기타 광학 및 정밀 기기 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523611' AS code, '가정용 고체 연료 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523612' AS code, '가정용 고체 연료 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523619' AS code, '가정용 고체 연료 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523621' AS code, '가정용 액체 연료 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523630' AS code, '가정용 가스 연료 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523910' AS code, '벽지, 마루덮개 및 장판류 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523921' AS code, '시계 및 귀금속 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523922' AS code, '시계 및 귀금속 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523923' AS code, '시계 및 귀금속 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523931' AS code, '운동 및 경기용품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523932' AS code, '운동 및 경기용품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523935' AS code, '기타 의복 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523936' AS code, '기타 의복 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523940' AS code, '게임용구, 인형 및 장난감 소매업' AS name, '문구·도서·완구' AS category
  UNION ALL
  SELECT '523951' AS code, '예술품 및 골동품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523952' AS code, '예술품 및 골동품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523954' AS code, '그 외 기타 분류 안된 상품 전문 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523961' AS code, '기념품, 관광 민예품 및 장식용품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523970' AS code, '자전거 및 기타 운송장비 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523980' AS code, '음반 및 비디오물 소매업' AS name, '문구·도서·완구' AS category
  UNION ALL
  SELECT '523981' AS code, '그 외 기타 분류 안된 가정용품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523982' AS code, '그 외 기타 분류 안된 가정용품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523983' AS code, '문구용품 및 회화용품 소매업' AS name, '문구·도서·완구' AS category
  UNION ALL
  SELECT '523984' AS code, '그 외 기타 분류 안된 상품 전문 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523985' AS code, '그 외 기타 분류 안된 상품 전문 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523986' AS code, '그 외 기타 분류 안된 상품 전문 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523987' AS code, '화초 및 식물 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523988' AS code, '화초 및 식물 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523990' AS code, '기타 여행 보조 및 예약 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '523993' AS code, '가방 및 기타 가죽제품 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '523994' AS code, '그 외 기타 분류 안된 상품 전문 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523995' AS code, '그 외 기타 분류 안된 상품 전문 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523996' AS code, '애완용 동물 및 관련용품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '523999' AS code, '그 외 기타 분류 안된 상품 전문 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '524000' AS code, '의복 액세서리 및 모조 장신구 소매업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '524001' AS code, '공구 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '524010' AS code, '중고 가구 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '524020' AS code, '기타 중고 상품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '524091' AS code, '기타 중고 상품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '524092' AS code, '중고 가전제품 및 통신장비 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '524093' AS code, '기타 중고 상품 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '525101' AS code, '전자상거래 소매업' AS name, '온라인쇼핑' AS category
  UNION ALL
  SELECT '525102' AS code, '기타 통신 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '525103' AS code, '전자상거래 소매 중개업' AS name, '온라인쇼핑' AS category
  UNION ALL
  SELECT '525104' AS code, 'SNS마켓' AS name, '온라인쇼핑' AS category
  UNION ALL
  SELECT '525105' AS code, '해외직구대행업' AS name, '온라인쇼핑' AS category
  UNION ALL
  SELECT '525200' AS code, '방문 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '525300' AS code, '계약배달 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '525910' AS code, '자동 판매기 운영업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '525911' AS code, '노점 및 유사 이동 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '525912' AS code, '그 외 기타 무점포 소매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '551001' AS code, '호텔업' AS name, '일반숙박업' AS category
  UNION ALL
  SELECT '551002' AS code, '여관업' AS name, '성인숙박업' AS category
  UNION ALL
  SELECT '551003' AS code, '청소년 수련시설 운영업' AS name, '기타' AS category
  UNION ALL
  SELECT '551004' AS code, '휴양 콘도 운영업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '551005' AS code, '민박업' AS name, '일반숙박업' AS category
  UNION ALL
  SELECT '551006' AS code, '기타 일반 및 생활 숙박시설 운영업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '551007' AS code, '숙박공유업' AS name, '일반숙박업' AS category
  UNION ALL
  SELECT '551009' AS code, '여관업' AS name, '성인숙박업' AS category
  UNION ALL
  SELECT '551010' AS code, '휴양 콘도 운영업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '551015' AS code, '기타 일반 및 생활 숙박시설 운영업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '551016' AS code, '기숙사 및 고시원 운영업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '551017' AS code, '그 외 기타 숙박업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552101' AS code, '한식 일반 음식점업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552102' AS code, '중식 음식점업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552103' AS code, '일식 음식점업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552104' AS code, '서양식 음식점업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552105' AS code, '출장 음식 서비스업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552107' AS code, '치킨 전문점' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552108' AS code, '김밥 및 기타 간이 음식점업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552109' AS code, '기관 구내식당업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552114' AS code, '한식 면 요리 전문점' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552115' AS code, '한식 육류 요리 전문점' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552116' AS code, '한식 해산물 요리 전문점' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552117' AS code, '기타 외국식 음식점업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552118' AS code, '피자, 햄버거, 샌드위치 및 유사 음식점업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552119' AS code, '김밥 및 기타 간이 음식점업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552123' AS code, '간이 음식 포장 판매 전문점' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552201' AS code, '일반 유흥 주점업' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '552202' AS code, '일반 유흥 주점업' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '552203' AS code, '무도 유흥 주점업' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '552204' AS code, '일반 유흥 주점업' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '552205' AS code, '생맥주 전문점' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '552206' AS code, '일반 유흥 주점업' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '552207' AS code, '기타 주점업' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '552208' AS code, '기타 주점업' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '552209' AS code, '기타 주점업' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '552210' AS code, '기타 주점업' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '552211' AS code, '기타 주점업' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '552301' AS code, '제과점업' AS name, '카페·디저트' AS category
  UNION ALL
  SELECT '552303' AS code, '커피 전문점' AS name, '카페·디저트' AS category
  UNION ALL
  SELECT '552305' AS code, '간이 음식 포장 판매 전문점' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552307' AS code, '기타 비알코올 음료점업' AS name, '카페·디저트' AS category
  UNION ALL
  SELECT '552308' AS code, '이동 음식점업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552309' AS code, '피자, 햄버거, 샌드위치 및 유사 음식점업' AS name, '외식·숙박' AS category
  UNION ALL
  SELECT '552310' AS code, '동물카페' AS name, '카페·디저트' AS category
  UNION ALL
  SELECT '601000' AS code, '도시철도 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '601001' AS code, '철도 여객 운송업' AS name, '대중교통' AS category
  UNION ALL
  SELECT '601002' AS code, '철도 화물 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602101' AS code, '시외버스 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602102' AS code, '시외버스 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602103' AS code, '시내버스 운송업' AS name, '대중교통' AS category
  UNION ALL
  SELECT '602104' AS code, '기타 도시 정기 육상 여객 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602109' AS code, '기타 도시 정기 육상 여객 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602110' AS code, '기타 도시 정기 육상 여객 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602201' AS code, '택시 운송업' AS name, '대중교통' AS category
  UNION ALL
  SELECT '602203' AS code, '택시 운송업' AS name, '대중교통' AS category
  UNION ALL
  SELECT '602204' AS code, '시내버스 운송업' AS name, '대중교통' AS category
  UNION ALL
  SELECT '602205' AS code, '전세버스 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602206' AS code, '택시 운송업' AS name, '대중교통' AS category
  UNION ALL
  SELECT '602209' AS code, '기타 부정기 여객 육상 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602301' AS code, '용달 화물 자동차 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602302' AS code, '일반 화물 자동차 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602303' AS code, '일반 화물 자동차 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602304' AS code, '일반 화물 자동차 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602305' AS code, '일반 화물 자동차 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602306' AS code, '특수 여객 자동차 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602307' AS code, '용달 화물 자동차 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602308' AS code, '용달 화물 자동차 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602309' AS code, '화물 운송 중개, 대리 및 관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '602310' AS code, '개별 화물 자동차 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602313' AS code, '기타 도로 화물 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602314' AS code, '일반 화물 자동차 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602315' AS code, '일반 화물 자동차 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '602316' AS code, '일반 화물 자동차 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '603000' AS code, '파이프라인 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '611001' AS code, '내항 여객 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '611002' AS code, '외항 화물 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '611003' AS code, '내항 화물 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '611004' AS code, '기타 해상 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '611005' AS code, '외항 여객 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '612000' AS code, '내륙 수상 여객 및 화물 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '612001' AS code, '항만 내 여객 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '612002' AS code, '기타 내륙 수상 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '612003' AS code, '수상 화물 취급업' AS name, '기타' AS category
  UNION ALL
  SELECT '621000' AS code, '항공 여객 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '621001' AS code, '항공 화물 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '630101' AS code, '항공 및 육상 화물 취급업' AS name, '기타' AS category
  UNION ALL
  SELECT '630102' AS code, '수상 화물 취급업' AS name, '기타' AS category
  UNION ALL
  SELECT '630201' AS code, '일반 창고업' AS name, '기타' AS category
  UNION ALL
  SELECT '630202' AS code, '냉장 및 냉동 창고업' AS name, '기타' AS category
  UNION ALL
  SELECT '630203' AS code, '농산물 창고업' AS name, '기타' AS category
  UNION ALL
  SELECT '630204' AS code, '위험 물품 보관업' AS name, '기타' AS category
  UNION ALL
  SELECT '630205' AS code, '기타 보관 및 창고업' AS name, '기타' AS category
  UNION ALL
  SELECT '630301' AS code, '철도 운송 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '630302' AS code, '여객 자동차 터미널 운영업' AS name, '기타' AS category
  UNION ALL
  SELECT '630303' AS code, '주차장 운영업' AS name, '기타' AS category
  UNION ALL
  SELECT '630304' AS code, '자동차 임대업(렌트카)' AS name, '기타' AS category
  UNION ALL
  SELECT '630305' AS code, '물류 터미널 운영업' AS name, '기타' AS category
  UNION ALL
  SELECT '630309' AS code, '기타 육상 운송지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '630311' AS code, '도로 및 관련시설 운영업' AS name, '기타' AS category
  UNION ALL
  SELECT '630401' AS code, '기타 수상 운송 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '630402' AS code, '기타 수상 운송 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '630403' AS code, '기타 수상 운송 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '630405' AS code, '항구 및 기타 해상 터미널 운영업' AS name, '기타' AS category
  UNION ALL
  SELECT '630500' AS code, '기타 항공 운송지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '630501' AS code, '공항 운영업' AS name, '기타' AS category
  UNION ALL
  SELECT '630600' AS code, '여행사업' AS name, '기타' AS category
  UNION ALL
  SELECT '630601' AS code, '기타 여행 보조 및 예약 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '630701' AS code, '시내버스 운송업' AS name, '대중교통' AS category
  UNION ALL
  SELECT '630702' AS code, '일반 화물 자동차 운송업' AS name, '기타' AS category
  UNION ALL
  SELECT '630901' AS code, '택배업' AS name, '기타' AS category
  UNION ALL
  SELECT '630902' AS code, '통관 대리 및 관련서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '630903' AS code, '화물 포장, 검수 및 계량 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '630904' AS code, '늘찬 배달업' AS name, '기타' AS category
  UNION ALL
  SELECT '630905' AS code, '화물 포장, 검수 및 계량 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '630909' AS code, '그 외 기타 분류 안된 운송관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '641100' AS code, '중앙은행' AS name, '기타' AS category
  UNION ALL
  SELECT '641200' AS code, '공영 우편업' AS name, '기타' AS category
  UNION ALL
  SELECT '641201' AS code, '택배업' AS name, '기타' AS category
  UNION ALL
  SELECT '642001' AS code, '무선 및 위성 통신업' AS name, '통신' AS category
  UNION ALL
  SELECT '642002' AS code, '통신 재판매업' AS name, '통신' AS category
  UNION ALL
  SELECT '642003' AS code, '그 외 기타 전기 통신업' AS name, '통신' AS category
  UNION ALL
  SELECT '642004' AS code, '포털 및 기타 인터넷 정보 매개 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '642005' AS code, '유선 통신업' AS name, '통신' AS category
  UNION ALL
  SELECT '659201' AS code, '국내은행' AS name, '기타' AS category
  UNION ALL
  SELECT '659202' AS code, '신용조합' AS name, '기타' AS category
  UNION ALL
  SELECT '659203' AS code, '그 외 기타 여신 금융업' AS name, '기타' AS category
  UNION ALL
  SELECT '659204' AS code, '그 외 기타 여신 금융업' AS name, '기타' AS category
  UNION ALL
  SELECT '659205' AS code, '상호 저축은행 및 기타 저축기관' AS name, '기타' AS category
  UNION ALL
  SELECT '659206' AS code, '신용카드 및 할부 금융업' AS name, '기타' AS category
  UNION ALL
  SELECT '659207' AS code, '외국은행' AS name, '기타' AS category
  UNION ALL
  SELECT '659208' AS code, '금융 리스업' AS name, '기타' AS category
  UNION ALL
  SELECT '659209' AS code, '개발 금융기관' AS name, '기타' AS category
  UNION ALL
  SELECT '659900' AS code, '기타 금융 투자업' AS name, '기타' AS category
  UNION ALL
  SELECT '659901' AS code, '그 외 기타 분류 안된 사업 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '659902' AS code, '그 외 기타 분류 안된 금융업' AS name, '기타' AS category
  UNION ALL
  SELECT '659903' AS code, '신탁업 및 집합 투자업' AS name, '기타' AS category
  UNION ALL
  SELECT '659904' AS code, '그 외 기타 여신 금융업' AS name, '기타' AS category
  UNION ALL
  SELECT '659905' AS code, '기금 운영업' AS name, '기타' AS category
  UNION ALL
  SELECT '659906' AS code, '지주회사' AS name, '기타' AS category
  UNION ALL
  SELECT '660100' AS code, '생명보험업' AS name, '기타' AS category
  UNION ALL
  SELECT '660101' AS code, '재보험업' AS name, '기타' AS category
  UNION ALL
  SELECT '660301' AS code, '손해보험업' AS name, '기타' AS category
  UNION ALL
  SELECT '660302' AS code, '손해보험업' AS name, '기타' AS category
  UNION ALL
  SELECT '660303' AS code, '손해보험업' AS name, '기타' AS category
  UNION ALL
  SELECT '660304' AS code, '보증보험업' AS name, '기타' AS category
  UNION ALL
  SELECT '660305' AS code, '건강보험업' AS name, '기타' AS category
  UNION ALL
  SELECT '660306' AS code, '산업 재해 및 기타 사회보장보험업' AS name, '기타' AS category
  UNION ALL
  SELECT '660307' AS code, '개인 공제업' AS name, '기타' AS category
  UNION ALL
  SELECT '660308' AS code, '사업 공제업' AS name, '기타' AS category
  UNION ALL
  SELECT '660309' AS code, '연금업' AS name, '기타' AS category
  UNION ALL
  SELECT '671201' AS code, '그 외 기타 금융 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '671202' AS code, '증권 중개업' AS name, '기타' AS category
  UNION ALL
  SELECT '671203' AS code, '선물 중개업' AS name, '기타' AS category
  UNION ALL
  SELECT '671900' AS code, '그 외 기타 금융 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '671901' AS code, '금융시장 관리업' AS name, '기타' AS category
  UNION ALL
  SELECT '671902' AS code, '증권 발행, 관리, 보관 및 거래 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '671903' AS code, '투자 자문업 및 투자 일임업' AS name, '기타' AS category
  UNION ALL
  SELECT '672000' AS code, '보험 대리 및 중개업' AS name, '기타' AS category
  UNION ALL
  SELECT '672001' AS code, '기타 보험 및 연금관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '701101' AS code, '주거용 건물 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '701102' AS code, '주거용 건물 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '701103' AS code, '주거용 건물 임대업(장기임대공동·단독주택)' AS name, '기타' AS category
  UNION ALL
  SELECT '701104' AS code, '주거용 건물 임대업(장기임대다가구주택)' AS name, '기타' AS category
  UNION ALL
  SELECT '701201' AS code, '비주거용 건물 임대업(점포, 자기땅)' AS name, '기타' AS category
  UNION ALL
  SELECT '701202' AS code, '비주거용 건물 임대업(점포, 타인땅)' AS name, '기타' AS category
  UNION ALL
  SELECT '701203' AS code, '비주거용 건물 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '701204' AS code, '비주거용 건물 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '701205' AS code, '비주거용 건물 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '701206' AS code, '비주거용 건물 임대업(점포, 자기땅)' AS name, '기타' AS category
  UNION ALL
  SELECT '701300' AS code, '비주거용 건물 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '701301' AS code, '주거용 건물 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '701302' AS code, '비주거용 건물 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '701400' AS code, '기타 부동산 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '701501' AS code, '비주거용 건물 임대업(자기땅)' AS name, '기타' AS category
  UNION ALL
  SELECT '701502' AS code, '비주거용 건물 임대업(타인땅)' AS name, '기타' AS category
  UNION ALL
  SELECT '701503' AS code, '기타 부동산 임대업(자기땅)' AS name, '기타' AS category
  UNION ALL
  SELECT '701504' AS code, '기타 부동산 임대업(타인땅)' AS name, '기타' AS category
  UNION ALL
  SELECT '701600' AS code, '무형 재산권 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '701700' AS code, '화장터 운영, 묘지 분양 및 관리업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '702001' AS code, '부동산 중개 및 대리업' AS name, '기타' AS category
  UNION ALL
  SELECT '702002' AS code, '부동산 감정 평가업' AS name, '기타' AS category
) AS src
JOIN `T_MCC_CTGR_C` ctgr ON ctgr.`name` = src.category;

-- batch 1201 ~ 1500
INSERT INTO `T_MCC_CODE_C` (`id`, `merchant_category_id`, `name`)
SELECT src.code, ctgr.id, src.name
FROM (
  SELECT '702003' AS code, '비주거용 부동산 관리업' AS name, '기타' AS category
  UNION ALL
  SELECT '702004' AS code, '부동산 투자 자문업' AS name, '기타' AS category
  UNION ALL
  SELECT '702005' AS code, '주거용 부동산 관리업' AS name, '기타' AS category
  UNION ALL
  SELECT '703011' AS code, '주거용 건물 개발 및 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '703012' AS code, '주거용 건물 개발 및 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '703014' AS code, '비주거용 건물 개발 및 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '703015' AS code, '기타 부동산 개발 및 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '703016' AS code, '비주거용 건물 개발 및 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '703017' AS code, '기타 부동산 개발 및 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '703021' AS code, '비주거용 건물 개발 및 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '703022' AS code, '비주거용 건물 개발 및 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '703023' AS code, '비주거용 건물 개발 및 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '703024' AS code, '비주거용 건물 개발 및 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '712100' AS code, '기타 산업용 기계 및 장비 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '712200' AS code, '기타 운송장비 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '712201' AS code, '기타 산업용 기계 및 장비 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '712202' AS code, '건설 및 토목공사용 기계ㆍ장비 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '712203' AS code, '컴퓨터 및 사무용 기계ㆍ장비 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '713001' AS code, '그 외 기타 개인 및 가정용품 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '713002' AS code, '스포츠 및 레크리에이션 용품 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '713003' AS code, '기타 산업용 기계 및 장비 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '713004' AS code, '음반 및 비디오물 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '713005' AS code, '서적 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '713006' AS code, '의류 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '721000' AS code, '컴퓨터 시스템 통합 자문 및 구축 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '721001' AS code, '컴퓨터시설 관리업' AS name, '기타' AS category
  UNION ALL
  SELECT '722000' AS code, '응용 소프트웨어 개발 및 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '722001' AS code, '유선 온라인 게임 소프트웨어 개발 및 공급업' AS name, '게임' AS category
  UNION ALL
  SELECT '722002' AS code, '모바일 게임 소프트웨어 개발 및 공급업' AS name, '게임' AS category
  UNION ALL
  SELECT '722003' AS code, '기타 게임 소프트웨어 개발 및 공급업' AS name, '게임' AS category
  UNION ALL
  SELECT '722004' AS code, '시스템 소프트웨어 개발 및 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '722005' AS code, '컴퓨터 프로그래밍 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '723000' AS code, '자료 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '723001' AS code, '호스팅 및 관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '724000' AS code, '데이터베이스 및 온라인 정보 제공업' AS name, '기타' AS category
  UNION ALL
  SELECT '724001' AS code, '뉴스 제공업' AS name, '기타' AS category
  UNION ALL
  SELECT '724002' AS code, '그 외 기타 정보 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '725000' AS code, '컴퓨터 및 주변 기기 수리업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '729000' AS code, '기타 정보 기술 및 컴퓨터 운영 관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '730000' AS code, '기타 인문 및 사회과학 연구개발업' AS name, '기타' AS category
  UNION ALL
  SELECT '730001' AS code, '물리, 화학 및 생물학 연구개발업' AS name, '기타' AS category
  UNION ALL
  SELECT '730002' AS code, '농림수산학 및 수의학 연구개발업' AS name, '기타' AS category
  UNION ALL
  SELECT '730003' AS code, '의학 및 약학 연구개발업' AS name, '기타' AS category
  UNION ALL
  SELECT '730004' AS code, '기타 자연과학 연구개발업' AS name, '기타' AS category
  UNION ALL
  SELECT '730005' AS code, '전기ㆍ전자공학 연구개발업' AS name, '기타' AS category
  UNION ALL
  SELECT '730006' AS code, '기타 공학 연구개발업' AS name, '기타' AS category
  UNION ALL
  SELECT '730007' AS code, '자연과학 및 공학 융합 연구개발업' AS name, '기타' AS category
  UNION ALL
  SELECT '730008' AS code, '경제 및 경영학 연구개발업' AS name, '기타' AS category
  UNION ALL
  SELECT '741101' AS code, '변호사업' AS name, '기타' AS category
  UNION ALL
  SELECT '741104' AS code, '변리사업' AS name, '기타' AS category
  UNION ALL
  SELECT '741106' AS code, '기타 법무관련 서비스업(공증인)' AS name, '기타' AS category
  UNION ALL
  SELECT '741107' AS code, '법무사업' AS name, '기타' AS category
  UNION ALL
  SELECT '741108' AS code, '기타 법무관련 서비스업(집행관)' AS name, '기타' AS category
  UNION ALL
  SELECT '741109' AS code, '기타 법무관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '741110' AS code, '기타 법무관련 서비스업(노무사)' AS name, '기타' AS category
  UNION ALL
  SELECT '741114' AS code, '기타 법무관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '741201' AS code, '세무사업' AS name, '기타' AS category
  UNION ALL
  SELECT '741202' AS code, '공인회계사업' AS name, '기타' AS category
  UNION ALL
  SELECT '741203' AS code, '세무사업(기장대리)' AS name, '기타' AS category
  UNION ALL
  SELECT '741204' AS code, '공인회계사업(기장대리)' AS name, '기타' AS category
  UNION ALL
  SELECT '741300' AS code, '시장 조사 및 여론 조사업' AS name, '기타' AS category
  UNION ALL
  SELECT '741400' AS code, '경영 컨설팅업' AS name, '기타' AS category
  UNION ALL
  SELECT '741401' AS code, '경영 컨설팅업(경영지도사)' AS name, '기타' AS category
  UNION ALL
  SELECT '741402' AS code, '직원 훈련기관' AS name, '기타' AS category
  UNION ALL
  SELECT '741403' AS code, '공공관계 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '742101' AS code, '측량업' AS name, '기타' AS category
  UNION ALL
  SELECT '742102' AS code, '지도 제작업' AS name, '기타' AS category
  UNION ALL
  SELECT '742103' AS code, '건축 설계 및 관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '742104' AS code, '기타 엔지니어링 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '742105' AS code, '건축 설계 및 관련 서비스업(건축사)' AS name, '기타' AS category
  UNION ALL
  SELECT '742106' AS code, '건물 및 토목 엔지니어링 서비스업(기술사)' AS name, '기타' AS category
  UNION ALL
  SELECT '742107' AS code, '도시 계획 및 조경 설계 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '742108' AS code, '제도업' AS name, '기타' AS category
  UNION ALL
  SELECT '742109' AS code, '건물 및 토목 엔지니어링 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '742110' AS code, '환경 관련 엔지니어링 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '742113' AS code, '지질 조사 및 탐사업' AS name, '기타' AS category
  UNION ALL
  SELECT '742201' AS code, '기타 기술 시험, 검사 및 분석업' AS name, '기타' AS category
  UNION ALL
  SELECT '742202' AS code, '기타 기술 시험, 검사 및 분석업(기술지도사)' AS name, '기타' AS category
  UNION ALL
  SELECT '742203' AS code, '물질 성분 검사 및 분석업' AS name, '기타' AS category
  UNION ALL
  SELECT '743001' AS code, '옥외 및 전시 광고업' AS name, '기타' AS category
  UNION ALL
  SELECT '743002' AS code, '광고 대행업' AS name, '기타' AS category
  UNION ALL
  SELECT '743003' AS code, '광고 매체 판매업' AS name, '기타' AS category
  UNION ALL
  SELECT '743004' AS code, '광고물 문안, 도안, 설계 등 작성업' AS name, '기타' AS category
  UNION ALL
  SELECT '743005' AS code, '그 외 기타 광고 관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749100' AS code, '고용 알선업' AS name, '기타' AS category
  UNION ALL
  SELECT '749101' AS code, '임시 및 일용 인력 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '749102' AS code, '상용 인력 공급 및 인사관리 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749200' AS code, '경비 및 경호 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749201' AS code, '보안 시스템 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749300' AS code, '건축물 일반 청소업' AS name, '기타' AS category
  UNION ALL
  SELECT '749301' AS code, '하수 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '749302' AS code, '소독, 구충 및 방제 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749303' AS code, '조경관리 및 유지 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749400' AS code, '인물 사진 및 행사용 영상 촬영업' AS name, '기타' AS category
  UNION ALL
  SELECT '749401' AS code, '그 외 기타 달리 분류되지 않은 개인 서비스업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '749402' AS code, '상업용 사진 촬영업' AS name, '기타' AS category
  UNION ALL
  SELECT '749403' AS code, '사진 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '749500' AS code, '포장 및 충전업' AS name, '기타' AS category
  UNION ALL
  SELECT '749601' AS code, '그 외 기타 분류 안된 사업 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749602' AS code, '그 외 기타 분류 안된 사업 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749603' AS code, '그 외 기타 분류 안된 사업 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749604' AS code, '그 외 기타 분류 안된 사업 지원 서비스업(기타임가공)' AS name, '기타' AS category
  UNION ALL
  SELECT '749609' AS code, '그 외 기타 분류 안된 사업 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749901' AS code, '문서 작성업' AS name, '기타' AS category
  UNION ALL
  SELECT '749902' AS code, '번역 및 통역 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749903' AS code, '사업 및 무형 재산권 중개업' AS name, '기타' AS category
  UNION ALL
  SELECT '749904' AS code, '손해 사정업' AS name, '기타' AS category
  UNION ALL
  SELECT '749905' AS code, '문서 작성업' AS name, '기타' AS category
  UNION ALL
  SELECT '749906' AS code, '통관 대리 및 관련서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749907' AS code, '전시, 컨벤션 및 행사 대행업' AS name, '기타' AS category
  UNION ALL
  SELECT '749909' AS code, '복사업' AS name, '기타' AS category
  UNION ALL
  SELECT '749910' AS code, '시각 디자인업' AS name, '기타' AS category
  UNION ALL
  SELECT '749911' AS code, '매니저업' AS name, '기타' AS category
  UNION ALL
  SELECT '749912' AS code, '물품 감정, 계량 및 견본 추출업' AS name, '기타' AS category
  UNION ALL
  SELECT '749913' AS code, '신용 조사 및 추심 대행업' AS name, '기타' AS category
  UNION ALL
  SELECT '749914' AS code, '인테리어 디자인업' AS name, '기타' AS category
  UNION ALL
  SELECT '749915' AS code, '제품 디자인업' AS name, '기타' AS category
  UNION ALL
  SELECT '749916' AS code, '패션, 섬유류 및 기타 전문 디자인업' AS name, '기타' AS category
  UNION ALL
  SELECT '749921' AS code, '그 외 기타 분류 안된 사업 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749927' AS code, '섬유, 의복, 신발 및 가죽제품 중개업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '749930' AS code, '도로 및 관련시설 운영업' AS name, '기타' AS category
  UNION ALL
  SELECT '749934' AS code, '무형 재산권 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '749935' AS code, '사업시설 유지ㆍ관리 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749937' AS code, '기타 사무 지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749938' AS code, '콜센터 및 텔레마케팅 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749939' AS code, '그 외 기타 분류 안된 전문, 과학 및 기술 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '749940' AS code, '재생용 재료 수집 및 판매업' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '749941' AS code, '사업 및 무형 재산권 중개업' AS name, '기타' AS category
  UNION ALL
  SELECT '749942' AS code, '기타 전문 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '804101' AS code, '외국인학교' AS name, '기타' AS category
  UNION ALL
  SELECT '809001' AS code, '운전학원' AS name, '기타' AS category
  UNION ALL
  SELECT '809002' AS code, '기타 기술 및 직업 훈련학원' AS name, '기타' AS category
  UNION ALL
  SELECT '809003' AS code, '레크리에이션 교육기관' AS name, '기타' AS category
  UNION ALL
  SELECT '809004' AS code, '일반 교과학원' AS name, '학원·교육' AS category
  UNION ALL
  SELECT '809005' AS code, '일반 교과학원' AS name, '학원·교육' AS category
  UNION ALL
  SELECT '809006' AS code, '그 외 기타 스포츠시설 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '809007' AS code, '일반 교과학원' AS name, '학원·교육' AS category
  UNION ALL
  SELECT '809009' AS code, '음악학원' AS name, '학원·교육' AS category
  UNION ALL
  SELECT '809010' AS code, '기타 기술 및 직업 훈련학원' AS name, '기타' AS category
  UNION ALL
  SELECT '809011' AS code, '운전학원' AS name, '기타' AS category
  UNION ALL
  SELECT '809012' AS code, '일반 교과학원' AS name, '학원·교육' AS category
  UNION ALL
  SELECT '809013' AS code, '컴퓨터 학원' AS name, '학원·교육' AS category
  UNION ALL
  SELECT '809014' AS code, '태권도 및 무술 교육기관' AS name, '기타' AS category
  UNION ALL
  SELECT '809015' AS code, '기타 스포츠 교육기관' AS name, '기타' AS category
  UNION ALL
  SELECT '809016' AS code, '온라인 교육학원' AS name, '학원·교육' AS category
  UNION ALL
  SELECT '809017' AS code, '외국어학원' AS name, '학원·교육' AS category
  UNION ALL
  SELECT '809018' AS code, '기타 교습학원' AS name, '학원·교육' AS category
  UNION ALL
  SELECT '809019' AS code, '기원 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '809020' AS code, '미술학원' AS name, '학원·교육' AS category
  UNION ALL
  SELECT '809021' AS code, '기타 예술학원' AS name, '학원·교육' AS category
  UNION ALL
  SELECT '809022' AS code, '그 외 기타 분류 안된 교육기관' AS name, '기타' AS category
  UNION ALL
  SELECT '841110' AS code, '입법기관' AS name, '기타' AS category
  UNION ALL
  SELECT '841121' AS code, '중앙 최고 집행기관' AS name, '기타' AS category
  UNION ALL
  SELECT '841131' AS code, '지방행정 집행기관' AS name, '기타' AS category
  UNION ALL
  SELECT '841141' AS code, '재정 및 경제정책 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '841190' AS code, '기타 일반 공공 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '841200' AS code, '정부기관 일반 보조 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '842110' AS code, '교육 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '842121' AS code, '문화 및 관광 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '842130' AS code, '환경 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '842140' AS code, '보건 및 복지 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '842190' AS code, '기타 사회서비스 관리 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '842211' AS code, '노동 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '842221' AS code, '농림수산 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '842231' AS code, '건설 및 운송 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '842241' AS code, '우편 및 통신 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '842291' AS code, '기타 산업 진흥 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '843100' AS code, '외무 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '843200' AS code, '국방 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '844010' AS code, '법원' AS name, '기타' AS category
  UNION ALL
  SELECT '844020' AS code, '검찰' AS name, '기타' AS category
  UNION ALL
  SELECT '844030' AS code, '교도기관' AS name, '기타' AS category
  UNION ALL
  SELECT '844040' AS code, '경찰' AS name, '기타' AS category
  UNION ALL
  SELECT '844050' AS code, '소방서' AS name, '기타' AS category
  UNION ALL
  SELECT '844090' AS code, '기타 사법 및 공공 질서 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '845000' AS code, '사회보장 행정' AS name, '기타' AS category
  UNION ALL
  SELECT '851100' AS code, '유아 교육기관' AS name, '기타' AS category
  UNION ALL
  SELECT '851101' AS code, '요양병원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851102' AS code, '치과병원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851103' AS code, '한방병원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851113' AS code, '종합병원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851114' AS code, '일반병원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851200' AS code, '초등학교' AS name, '기타' AS category
  UNION ALL
  SELECT '851201' AS code, '일반의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851202' AS code, '일반의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851203' AS code, '일반의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851204' AS code, '일반의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851205' AS code, '일반의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851206' AS code, '일반의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851207' AS code, '일반의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851208' AS code, '방사선 진단 및 병리 검사 의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851209' AS code, '일반의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851211' AS code, '치과의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851212' AS code, '한의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851219' AS code, '일반의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851901' AS code, '그 외 기타 보건업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851902' AS code, '유사 의료업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851903' AS code, '유사 의료업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851904' AS code, '유사 의료업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851905' AS code, '방사선 진단 및 병리 검사 의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851906' AS code, '그 외 기타 보건업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851907' AS code, '그 외 기타 보건업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851908' AS code, '유사 의료업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851909' AS code, '방사선 진단 및 병리 검사 의원' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851911' AS code, '앰뷸런스 서비스업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '851912' AS code, '유사 의료업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '852000' AS code, '수의업' AS name, '기타' AS category
  UNION ALL
  SELECT '852110' AS code, '중학교' AS name, '기타' AS category
  UNION ALL
  SELECT '852120' AS code, '일반 고등학교' AS name, '기타' AS category
  UNION ALL
  SELECT '852210' AS code, '상업 및 정보산업 특성화 고등학교' AS name, '기타' AS category
  UNION ALL
  SELECT '852220' AS code, '공업 특성화 고등학교' AS name, '기타' AS category
  UNION ALL
  SELECT '852290' AS code, '기타 특성화 고등학교' AS name, '기타' AS category
  UNION ALL
  SELECT '853010' AS code, '전문대학' AS name, '기타' AS category
  UNION ALL
  SELECT '853020' AS code, '대학교' AS name, '기타' AS category
  UNION ALL
  SELECT '853030' AS code, '대학원' AS name, '기타' AS category
  UNION ALL
  SELECT '854100' AS code, '특수학교' AS name, '기타' AS category
  UNION ALL
  SELECT '854300' AS code, '대안학교' AS name, '기타' AS category
  UNION ALL
  SELECT '856400' AS code, '사회교육시설' AS name, '기타' AS category
  UNION ALL
  SELECT '863000' AS code, '공중 보건 의료업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '871110' AS code, '노인 요양 복지시설 운영업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '871210' AS code, '신체 부자유자 거주 복지시설 운영업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '871220' AS code, '정신 질환, 정신 지체 및 약물 중독자 거주 복지시설 운영업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '871310' AS code, '아동 및 부녀자 거주 복지시설 운영업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '871390' AS code, '그 외 기타 거주 복지시설 운영업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '872100' AS code, '보육시설 운영업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '872910' AS code, '직업 재활원 운영업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '872920' AS code, '종합복지관 운영업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '872930' AS code, '방문 복지서비스 제공업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '872940' AS code, '사회복지 상담 서비스 제공업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '872990' AS code, '그 외 기타 비거주 복지 서비스업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '900100' AS code, '지정 외 폐기물 수집, 운반업' AS name, '기타' AS category
  UNION ALL
  SELECT '900101' AS code, '지정 외 폐기물 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '900102' AS code, '방사성 폐기물 수집, 운반 및 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '900103' AS code, '지정 폐기물 수집, 운반업' AS name, '기타' AS category
  UNION ALL
  SELECT '900104' AS code, '건설 폐기물 수집, 운반업' AS name, '기타' AS category
  UNION ALL
  SELECT '900105' AS code, '지정 폐기물 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '900106' AS code, '건설 폐기물 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '900200' AS code, '하수 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '900201' AS code, '사람 분뇨 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '900202' AS code, '폐수 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '900203' AS code, '축산 분뇨 처리업' AS name, '기타' AS category
  UNION ALL
  SELECT '900300' AS code, '금속류 해체 및 선별업' AS name, '기타' AS category
  UNION ALL
  SELECT '900900' AS code, '산업설비, 운송장비 및 공공장소 청소업' AS name, '기타' AS category
  UNION ALL
  SELECT '921100' AS code, '영화, 비디오물 및 방송 프로그램 배급업' AS name, '기타' AS category
  UNION ALL
  SELECT '921200' AS code, '영화관 운영업' AS name, '영화·공연·테마파크' AS category
  UNION ALL
  SELECT '921301' AS code, '라디오 방송업' AS name, '기타' AS category
  UNION ALL
  SELECT '921302' AS code, '지상파 방송업' AS name, '기타' AS category
  UNION ALL
  SELECT '921303' AS code, '유선 방송업' AS name, '기타' AS category
  UNION ALL
  SELECT '921304' AS code, '방송 프로그램 제작업' AS name, '기타' AS category
  UNION ALL
  SELECT '921305' AS code, '위성 및 기타 방송업' AS name, '기타' AS category
  UNION ALL
  SELECT '921306' AS code, '프로그램 공급업' AS name, '기타' AS category
  UNION ALL
  SELECT '921401' AS code, '공연 기획업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '921402' AS code, '무용 및 음악단체' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '921403' AS code, '연극단체' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '921404' AS code, '비주거용 건물 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '921405' AS code, '공연 및 제작관련 대리업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '921406' AS code, '그 외 기타 창작 및 예술관련 서비스업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '921407' AS code, '기타 공연단체' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '921501' AS code, '광고 영화 및 비디오물 제작업' AS name, '기타' AS category
  UNION ALL
  SELECT '921502' AS code, '일반 영화 및 비디오물 제작업' AS name, '기타' AS category
  UNION ALL
  SELECT '921503' AS code, '영화, 비디오물 및 방송 프로그램 제작 관련 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '921504' AS code, '애니메이션 영화 및 비디오물 제작업' AS name, '기타' AS category
  UNION ALL
  SELECT '921505' AS code, '미디어콘텐츠창작업' AS name, '기타' AS category
  UNION ALL
  SELECT '921901' AS code, '공연시설 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '921902' AS code, '레크리에이션 교육기관' AS name, '기타' AS category
  UNION ALL
  SELECT '921903' AS code, '유원지 및 테마파크 운영업' AS name, '영화·공연·테마파크' AS category
  UNION ALL
  SELECT '921904' AS code, '무도장 운영업' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '922101' AS code, '그 외 기타 개인 및 가정용품 수리업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '922102' AS code, '기타 일반 기계 및 장비 수리업' AS name, '기타' AS category
  UNION ALL
  SELECT '922103' AS code, '전기ㆍ전자 및 정밀 기기 수리업' AS name, '기타' AS category
  UNION ALL
  SELECT '922104' AS code, '가전제품 수리업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '922105' AS code, '의복 및 기타 가정용 직물제품 수리업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '922106' AS code, '건설ㆍ광업용 기계 및 장비 수리업' AS name, '기타' AS category
  UNION ALL
  SELECT '922107' AS code, '통신장비 수리업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '922108' AS code, '가죽, 가방 및 신발 수리업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '922109' AS code, '시계, 귀금속 및 악기 수리업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '922201' AS code, '자동차 종합 수리업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '922202' AS code, '자동차 전문 수리업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '922203' AS code, '자동차 세차업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '922204' AS code, '모터사이클 수리업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '923100' AS code, '독서실 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '923101' AS code, '도서관 및 기록 보존소 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '923102' AS code, '독서실 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '923200' AS code, '박물관 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '923201' AS code, '사적지 관리 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '923300' AS code, '식물원 및 동물원 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '923301' AS code, '기타 유사 여가관련 서비스업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924101' AS code, '그 외 기타 스포츠 서비스업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924102' AS code, '그 외 기타 스포츠 서비스업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924103' AS code, '스포츠 클럽 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924200' AS code, '실내 경기장 운영업' AS name, '영화·공연·테마파크' AS category
  UNION ALL
  SELECT '924201' AS code, '실외 경기장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924202' AS code, '경주장 및 동물 경기장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924302' AS code, '볼링장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924303' AS code, '골프장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924304' AS code, '스키장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924305' AS code, '체력 단련시설 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924306' AS code, '그 외 기타 스포츠시설 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924307' AS code, '골프 연습장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924308' AS code, '골프 연습장 운영업' AS name, '문화·여가' AS category
) AS src
JOIN `T_MCC_CTGR_C` ctgr ON ctgr.`name` = src.category;

-- batch 1501 ~ 1611
INSERT INTO `T_MCC_CODE_C` (`id`, `merchant_category_id`, `name`)
SELECT src.code, ctgr.id, src.name
FROM (
  SELECT '924309' AS code, '당구장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924310' AS code, '그 외 기타 스포츠시설 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924311' AS code, '수영장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924312' AS code, '그 외 기타 스포츠시설 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924313' AS code, '종합 스포츠시설 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924314' AS code, '그 외 기타 스포츠시설 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924901' AS code, '녹음시설 운영업' AS name, '기타' AS category
  UNION ALL
  SELECT '924902' AS code, '기타 오락장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924903' AS code, '노래 연습장 운영업' AS name, 'PC방·노래방' AS category
  UNION ALL
  SELECT '924904' AS code, '기타 오락장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924905' AS code, '자연공원 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924906' AS code, '복권 발행 및 판매업' AS name, '사행성·도박' AS category
  UNION ALL
  SELECT '924907' AS code, '전자 게임장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924908' AS code, '전자 게임장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924909' AS code, '컴퓨터 게임방 운영업' AS name, 'PC방·노래방' AS category
  UNION ALL
  SELECT '924910' AS code, '비디오물 감상실 운영업' AS name, '기타' AS category
  UNION ALL
  SELECT '924911' AS code, '그 외 기타 분류 안된 오락관련 서비스업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924912' AS code, '전자 게임장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924913' AS code, '기타 오락장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924914' AS code, '낚시장 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924915' AS code, '기타 수상오락 서비스업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924916' AS code, '체육 공원 및 유사 공원 운영업' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '924917' AS code, '기타 사행시설 관리 및 운영업' AS name, '사행성·도박' AS category
  UNION ALL
  SELECT '930100' AS code, '가정용 세탁업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930101' AS code, '산업용 세탁업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930102' AS code, '세탁물 공급업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930201' AS code, '이용업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930203' AS code, '두발 미용업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '930205' AS code, '피부 미용업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '930207' AS code, '기타 미용업' AS name, '패션·뷰티' AS category
  UNION ALL
  SELECT '930208' AS code, '마사지업' AS name, '유흥·성인업소' AS category
  UNION ALL
  SELECT '930209' AS code, '체형 등 기타 신체 관리 서비스업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930301' AS code, '장례식장 및 장의관련 서비스업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930302' AS code, '화장터 운영, 묘지 분양 및 관리업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930901' AS code, '예식장업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930902' AS code, '욕탕업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930903' AS code, '의류 임대업' AS name, '기타' AS category
  UNION ALL
  SELECT '930904' AS code, '인물 사진 및 행사용 영상 촬영업' AS name, '기타' AS category
  UNION ALL
  SELECT '930908' AS code, '그 외 기타 달리 분류되지 않은 개인 서비스업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930909' AS code, '점술 및 유사 서비스업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930911' AS code, '그 외 기타 달리 분류되지 않은 개인 서비스업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930912' AS code, '결혼 상담 및 준비 서비스업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930913' AS code, '개인 간병 및 유사 서비스업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930914' AS code, '노인 양로 복지시설 운영업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '930915' AS code, '기타 교육지원 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '930916' AS code, '탐정 및 조사 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '930917' AS code, '콜센터 및 텔레마케팅 서비스업' AS name, '기타' AS category
  UNION ALL
  SELECT '930919' AS code, '애완 동물 장묘 및 보호 서비스업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '930921' AS code, '교육관련 자문 및 평가업' AS name, '기타' AS category
  UNION ALL
  SELECT '930925' AS code, '그 외 기타 달리 분류되지 않은 개인 서비스업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '940100' AS code, '작가' AS name, '기타' AS category
  UNION ALL
  SELECT '940200' AS code, '화가 및 관련예술가' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '940301' AS code, '작곡가' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '940302' AS code, '배우,탤런트등' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '940303' AS code, '모델' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '940304' AS code, '가수' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '940305' AS code, '성악가 등' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '940306' AS code, '1인미디어콘텐츠창작자' AS name, '기타' AS category
  UNION ALL
  SELECT '940500' AS code, '연예보조서비스' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '940600' AS code, '자문,감독,지도료,고문료,교정료' AS name, '기타' AS category
  UNION ALL
  SELECT '940901' AS code, '바둑기사' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '940902' AS code, '꽃꽂이교사' AS name, '기타' AS category
  UNION ALL
  SELECT '940903' AS code, '학원강사,강사,과외교습자,재단사' AS name, '기타' AS category
  UNION ALL
  SELECT '940904' AS code, '직업운동가' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '940905' AS code, '유흥접객원 및 댄서' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '940906' AS code, '보험설계사' AS name, '기타' AS category
  UNION ALL
  SELECT '940907' AS code, '음료품배달원' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '940908' AS code, '서적방문판매원,학습지방문판매원,화장품방문판매원,정수기방문판매원,자동차방문판매원,일반(기타)방문판매원' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '940909' AS code, '기타자영업' AS name, '기타' AS category
  UNION ALL
  SELECT '940910' AS code, '다단계판매원의후원수당' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '940911' AS code, '기타모집수당,채권회수수당' AS name, '기타' AS category
  UNION ALL
  SELECT '940912' AS code, '개인간병인' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '940913' AS code, '대리운전기사' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '940914' AS code, '골프장캐디' AS name, '문화·여가' AS category
  UNION ALL
  SELECT '940915' AS code, '목욕관리사' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '940916' AS code, '행사도우미' AS name, '기타' AS category
  UNION ALL
  SELECT '940917' AS code, '심부름용역원' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '940918' AS code, '퀵서비스배달원' AS name, '기타' AS category
  UNION ALL
  SELECT '940919' AS code, '기타물품운반원' AS name, '기타' AS category
  UNION ALL
  SELECT '940920' AS code, '학습지 방문강사' AS name, '기타' AS category
  UNION ALL
  SELECT '940921' AS code, '교육교구 방문강사' AS name, '기타' AS category
  UNION ALL
  SELECT '940922' AS code, '대여제품 방문점검원' AS name, '기타' AS category
  UNION ALL
  SELECT '940923' AS code, '대출모집인' AS name, '기타' AS category
  UNION ALL
  SELECT '940924' AS code, '신용카드회원 모집인' AS name, '기타' AS category
  UNION ALL
  SELECT '940925' AS code, '방과후강사' AS name, '기타' AS category
  UNION ALL
  SELECT '940926' AS code, '소프트웨어 프리랜서' AS name, '기타' AS category
  UNION ALL
  SELECT '940927' AS code, '관광통역 안내사' AS name, '기타' AS category
  UNION ALL
  SELECT '940928' AS code, '어린이 통학버스 기사' AS name, '기타' AS category
  UNION ALL
  SELECT '940929' AS code, '중고자동차 판매원' AS name, '생활용품·잡화' AS category
  UNION ALL
  SELECT '941100' AS code, '산업 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '941200' AS code, '전문가 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '942000' AS code, '노동조합' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949111' AS code, '불교 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949121' AS code, '기독교 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949131' AS code, '천주교 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949141' AS code, '민족 종교 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949142' AS code, '천도교 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949143' AS code, '원불교 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949190' AS code, '기타 종교 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949191' AS code, '유교 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949200' AS code, '정치 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949311' AS code, '환경운동 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949390' AS code, '기타 시민운동 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949900' AS code, '그 외 기타 협회 및 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949901' AS code, '그 외 기타 협회 및 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '949902' AS code, '그 외 기타 협회 및 단체' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '950000' AS code, '그 외 기타 달리 분류되지 않은 개인 서비스업' AS name, '생활서비스' AS category
  UNION ALL
  SELECT '950001' AS code, '가구 내 고용활동' AS name, '기타' AS category
  UNION ALL
  SELECT '950002' AS code, '보육시설 운영업' AS name, '의료·건강' AS category
  UNION ALL
  SELECT '990010' AS code, '주한 외국 공관' AS name, '기타' AS category
  UNION ALL
  SELECT '990090' AS code, '기타 국제 및 외국기관' AS name, '기타' AS category
) AS src
JOIN `T_MCC_CTGR_C` ctgr ON ctgr.`name` = src.category;

-- 아래 기능 데이터는 위 MCC 기준 데이터를 사용한다.
-- T_MCC_POLICY_M: 자녀 2에게 PC방·노래방 커스텀 정책 (부모 1명 고정 -> child_id+category만 UNIQUE)
--
-- 2번째 행(편의점 WATCH, 기준 3회)은 원래 V003 에 들어 있던 행이다. 특정 회원 아이디를
-- 가정한 시드 데이터라 마이그레이션에서 이쪽으로 옮겼다.
-- watch_threshold_count 는 V003 이 추가한 컬럼이라 마이그레이션을 건너뛰면 이 문장이 깨진다.
--
-- 편의점은 default_policy 가 ALLOW 라, 커스텀 WATCH 가 기본정책을 덮어쓰는지 확인할 수 있다.
-- (PC방·노래방은 기본이 이미 WATCH 라 BLOCK 으로 덮는 방향만 검증된다)
INSERT INTO `T_MCC_POLICY_M` (`id`, `parent_id`, `child_id`, `merchant_category_id`, `policy`, `watch_threshold_count`)
VALUES
(1, 1, 2, (SELECT `id` FROM `T_MCC_CTGR_C` WHERE `name` = 'PC방·노래방'), 'BLOCK', NULL),
(2, 1, 2, (SELECT `id` FROM `T_MCC_CTGR_C` WHERE `name` = '편의점'), 'WATCH', 3);

-- T_WLT_BASE_M: 회원 지갑 3개(부모1 + 자녀2) + 자녀2의 예금/적금 전용 지갑 2개
-- (member_id UNIQUE가 아니라 INDEX로 수정되어 있어서 한 회원이 지갑 여러 개 가져도 정상 통과)
--
-- ★ 여기 balance 는 임의의 값이 아니라 이 파일 아래쪽 T_WLT_HIST_H 의 마지막 balance_after 다.
--   잔액조회 API는 이 컬럼을, 거래내역 API는 T_WLT_HIST_H 를 읽으므로 둘이 어긋나면
--   같은 화면 두 곳이 다른 금액을 보여준다. 아래 이체/충전/결제를 하나라도 손대면
--   T_WLT_HIST_H 와 이 다섯 값을 같이 다시 계산해야 한다. 근거는 파일 끝 "잔액 검증" 주석 참고.
--
--   1 부모     500,000 +9,000(대출상환 수취)                        = 509,000
--   2 자녀2    200,000 -100,000(적금) -50,000(예금) +50,000(충전) -3,500(결제) = 96,500
--   3 자녀3     10,000 -9,000(대출상환)                             =   1,000
--   4 예금            0 +50,000(예치)                               =  50,000
--   5 적금            0 +100,000(1회차 납입)                        = 100,000
INSERT INTO `T_WLT_BASE_M` (`id`, `member_id`, `balance`, `type`)
VALUES
(1, 1, 509000, 'MEMBER'),
(2, 2, 96500, 'MEMBER'),
(3, 3, 1000, 'MEMBER'),
(4, 2, 50000, 'DEPOSIT'),
(5, 2, 100000, 'SAVING');

-- T_PAY_METHOD_M: 부모1의 카드/계좌
INSERT INTO `T_PAY_METHOD_M` (`id`, `parent_id`, `billing_key`, `type`, `card_company`, `masked_card_number`, `is_primary`, `status`)
VALUES (1, 1, 'billing-key-card-001', 'CARD', '신한카드', '1234-56**-****-7890', TRUE, 'ACTIVE');

INSERT INTO `T_PAY_METHOD_M` (`id`, `parent_id`, `billing_key`, `type`, `account_bank_name`, `account_number`, `is_primary`, `status`)
VALUES (2, 1, 'billing-key-account-001', 'ACCOUNT', '카카오뱅크', '3333-**-*****1', FALSE, 'ACTIVE');

-- T_DPT_PROD_M / T_SVG_PROD_M 상품. 대출 상품은 V010에서 등급별로 생성된다.
INSERT INTO `T_DPT_PROD_M` (`id`, `name`, `interest_calculation_type`, `rate_1m`, `rate_3m`, `rate_6m`, `rate_12m`, `early_termination_rate`, `min_amount`, `max_amount`)
VALUES (1, '티니 자유예금', 'SIMPLE', 1.5, 2.0, 2.5, 3.0, 0.5, 10000, 5000000);

INSERT INTO `T_SVG_PROD_M` (`id`, `name`, `savings_type`, `interest_calculation_type`, `rate_12m`, `early_termination_rate`, `min_month_amount`, `max_month_amount`)
VALUES
(1, '티니 정기적금', 'FIXED', 'SIMPLE', 4.0, 1.0, 10000, 500000),
(2, '티니 자유적금', 'FREE', 'SIMPLE', 3.5, 1.0, 1000, 500000);

-- T_DPT_ENROLL_M: 자녀2가 예금 가입, 승인 완료 상태
-- ACTIVE 예금은 반드시 예치금이 들어와 있어야 한다. 아래 transfer 3 이 그 입금이고
-- 예치액 50,000원은 상품 min_amount(10,000) 이상이다. 이 이체 없이 wallet 4 를 0으로 두면
-- 애초에 개설될 수 없는 예금이 되고, 이자 계산 로직을 돌려도 0이 나와 조용히 통과한다.
INSERT INTO `T_DPT_ENROLL_M`
	(`id`, `deposit_id`, `child_id`, `parent_id`, `wallet_id`, `applied_rate`, `applied_early_termination_rate`, `term_months`, `start_date`, `maturity_date`, `status`)
VALUES (1, 1, 2, 1, 4, 4.0, 0.5, 12, '2026-07-01', '2027-07-01', 'ACTIVE');

-- T_WLT_TRF_L: 예금 가입 예치금 이체 (자녀2 지갑 -> 예금 지갑), 가입 확정일과 같은 날
INSERT INTO `T_WLT_TRF_L` (`id`, `from_wallet_id`, `to_wallet_id`, `idempotency_key`, `amount`, `type`, `status`, `created_at`)
VALUES (3, 2, 4, UUID(), 50000, 'DEPOSIT', 'COMPLETED', '2026-07-01 09:00:00');

-- T_SVG_ENROLL_M: 자녀2가 정기적금 가입
-- start_date 가 2026-06-25 인 이유: payment_day 25 기준으로 1회차 2026-06-25(납입),
-- 2회차 2026-07-25(미납), 3회차 2026-08-25(아직 미도래) 가 되어야 아래 MISSED 행이 성립한다.
-- start_date 를 7/25로 두면 2회차 만기일이 미래라, 연체 배치가 상태를 재계산하는 순간 MISSED 가 뒤집힌다.
INSERT INTO `T_SVG_ENROLL_M`
	(`id`, `saving_id`, `child_id`, `parent_id`, `wallet_id`, `applied_rate`, `applied_early_termination_rate`, `term_months`, `monthly_amount`, `payment_day`, `auto_transfer`, `start_date`, `maturity_date`, `status`)
VALUES (1, 1, 2, 1, 5, 5.0, 1.0, 12, 100000, 25, TRUE, '2026-06-25', '2027-06-25', 'ACTIVE');

-- T_LON_ENROLL_M: 자녀3가 대출 실행 후 1회차 상환까지 마친 상태
--
-- outstanding_principal 과 paid_count 는 아래 T_LON_REPAYHIST_H 를 반영한 캐시 컬럼이다.
-- 컬럼 주석이 각각 '남은 원금 (상환시 감소 캐시)', '성공적으로 지불한 횟수'이므로
-- 1회차에서 원금 8,000을 갚았으면 100,000 - 8,000 = 92,000 이고 paid_count 는 1이다.
-- 실행 직후(상환 이력 0건) 상태를 원하면 아래 상환 이력과 transfer 2 를 같이 지워야 한다.
--
-- applied_rate / applied_late_fee_rate 는 상품의 base_rate / late_fee_rate 를 그대로 스냅샷한다.
-- 예적금은 등급 우대금리(bonus_rate)라는 문서화된 가산 규칙이 있어 3.0+1.00, 4.0+1.00 이지만
-- 대출에는 그런 규칙이 없다. 근거 없이 +0.5 를 얹으면 금리 산출 로직의 기대값을 만들 수 없다.
INSERT INTO `T_LON_ENROLL_M`
	(`id`, `loan_id`, `parent_id`, `child_id`, `principal_amount`, `outstanding_principal`, `overdue_interest`, `applied_rate`, `applied_late_fee_rate`, `auto_transfer`, `payment_day`, `paid_count`, `term_months`, `start_date`, `maturity_date`, `status`)
VALUES (1, (SELECT `id` FROM `T_LON_PROD_M` WHERE `name` = '스타터 원리금균등 대출'),
        1, 3, 100000, 92000, 0, 7.0, 8.0, TRUE, 25, 1, 12, '2026-07-01', '2027-07-01', 'ACTIVE');

-- T_WLT_TRF_L: 적금 1회차 자동이체 성공 건
INSERT INTO `T_WLT_TRF_L` (`id`, `from_wallet_id`, `to_wallet_id`, `idempotency_key`, `amount`, `type`, `status`, `created_at`)
VALUES (1, 2, 5, UUID(), 100000, 'SAVING', 'COMPLETED', '2026-06-25 09:00:00');

-- T_SVG_PAYHIST_H: 위 transfer와 연결된 1회차 납입 완료
INSERT INTO `T_SVG_PAYHIST_H`
	(`id`, `saving_enrollment_id`, `transfer_id`, `installment_no`, `amount`, `paid_amount`, `status`, `paid_at`)
VALUES (1, 1, 1, 1, 100000, 100000, 'PAID', '2026-06-25 09:00:00');

-- T_SVG_PAYHIST_H: 2회차(2026-07-25 만기)는 잔액부족으로 시도 자체가 없어 MISSED, transfer_id NULL
-- 실제로 그 시점 자녀2 지갑은 50,000원이라 100,000원을 뺄 수 없었다 (아래 T_WLT_HIST_H 참고).
-- 3회차(2026-08-25)는 아직 만기가 오지 않았고, 이 테이블에 '미도래' 상태가 없으므로 행 자체를 넣지 않는다.
INSERT INTO `T_SVG_PAYHIST_H`
	(`id`, `saving_enrollment_id`, `transfer_id`, `installment_no`, `amount`, `paid_amount`, `status`)
VALUES (2, 1, NULL, 2, 100000, 0, 'MISSED');

-- T_WLT_TRF_L: 대출 1회차 상환 성공 건 (payment_day 25 -> 2026-07-25)
INSERT INTO `T_WLT_TRF_L` (`id`, `from_wallet_id`, `to_wallet_id`, `idempotency_key`, `amount`, `type`, `status`, `created_at`)
VALUES (2, 3, 1, UUID(), 9000, 'LOAN', 'COMPLETED', '2026-07-25 09:00:00');

-- T_LON_REPAYHIST_H: 위 transfer와 연결된 1회차 상환 완료 (원금 8,000 + 이자 1,000 = 이체액 9,000)
INSERT INTO `T_LON_REPAYHIST_H`
	(`id`, `loan_enrollment_id`, `transfer_id`, `installment_no`, `principal_amount`, `paid_principal_amount`, `interest_amount`, `paid_interest_amount`, `status`, `paid_at`)
VALUES (1, 1, 2, 1, 8000, 8000, 1000, 1000, 'PAID', '2026-07-25 09:00:00');

-- T_WLT_CHARGE_L: 2회차 미납을 본 부모1이 카드로 자녀2 지갑에 충전
INSERT INTO `T_WLT_CHARGE_L` (`id`, `wallet_id`, `payment_method_id`, `idempotency_key`, `amount`, `status`, `order_id`, `payment_key`, `created_at`)
VALUES (1, 2, 1, UUID(), 50000, 'SUCCESS', 'charge-order-0001', 'toss-payment-key-charge-0001', '2026-07-28 20:00:00');

-- T_PAY_TRAN_L: 자녀2 QR결제 성공 건 (편의점 카테고리)
--
-- applied_policy 가 WATCH 인 이유: 편의점의 default_policy 는 ALLOW 지만, 위 T_MCC_POLICY_M 에
-- 자녀2 전용 WATCH 정책이 있고 커스텀 정책이 기본정책을 덮는다(스키마 주석: "이 행이 없으면
-- category.default_policy 적용"). 여기에 ALLOW 를 남겨두면 정책 판정 로직이 커스텀 정책을
-- 무시해도 시드 값과 일치해 테스트가 통과한다. WATCH 는 관찰이지 차단이 아니므로 결제는 SUCCESS 다.
INSERT INTO `T_PAY_TRAN_L` (`id`, `wallet_id`, `category_id`, `idempotency_key`, `applied_policy`, `merchant_name`, `amount`, `status`, `order_id`, `payment_key`, `created_at`)
VALUES (1, 2,
        (SELECT `id` FROM `T_MCC_CTGR_C` WHERE `name` = '편의점'),
        UUID(), 'WATCH', '티니편의점', 3500, 'SUCCESS', 'order-0001', 'toss-payment-key-0001', '2026-07-30 18:00:00');

-- ============================================================
-- T_WLT_HIST_H: 위의 모든 이체/충전/결제를 양쪽 지갑에 빠짐없이 반영한 원장
--
-- ★ 규칙: COMPLETED 이체 한 건은 반드시 두 행을 만든다 (보낸 지갑 DEBIT + 받은 지갑 CREDIT).
--   한쪽만 넣으면 이체는 완료됐는데 받는 지갑 잔액이 그대로인 상태가 되고,
--   이체와 원장을 조인하는 정합성 조회에서 고아 행으로 잡힌다.
--
-- 시간순 원장 (id 순서 = created_at 순서):
--
--   지갑2(자녀2)  시작 200,000
--     06-25  적금 1회차 이체   -100,000 -> 100,000   (transfer 1)
--     07-01  예금 예치금 이체   -50,000 ->  50,000   (transfer 3)
--     07-25  적금 2회차 시도 실패 (잔액 50,000 < 100,000) -> 원장 기록 없음, MISSED 행만 남음
--     07-28  카드 충전          +50,000 -> 100,000   (charge 1)
--     07-30  편의점 결제         -3,500 ->  96,500   (payment 1)
--
--   지갑5(적금)   0 +100,000 -> 100,000
--   지갑4(예금)   0  +50,000 ->  50,000
--   지갑3(자녀3)  10,000 -9,000 -> 1,000   (transfer 2, 대출 1회차 상환)
--   지갑1(부모)  500,000 +9,000 -> 509,000 (transfer 2 수취)
--
-- 각 지갑의 마지막 balance_after 가 T_WLT_BASE_M.balance 와 같아야 한다.
-- ============================================================
INSERT INTO `T_WLT_HIST_H` (`id`, `wallet_id`, `payment_id`, `transfer_id`, `charge_id`, `direction`, `amount`, `balance_after`, `description`, `created_at`)
VALUES
(1, 2, NULL, 1, NULL, 'DEBIT', 100000, 100000, '적금 1회차 자동이체', '2026-06-25 09:00:00'),
(2, 5, NULL, 1, NULL, 'CREDIT', 100000, 100000, '적금 1회차 납입', '2026-06-25 09:00:00'),
(3, 2, NULL, 3, NULL, 'DEBIT', 50000, 50000, '예금 가입 예치', '2026-07-01 09:00:00'),
(4, 4, NULL, 3, NULL, 'CREDIT', 50000, 50000, '예금 예치금 입금', '2026-07-01 09:00:00'),
(5, 3, NULL, 2, NULL, 'DEBIT', 9000, 1000, '대출 1회차 상환', '2026-07-25 09:00:00'),
(6, 1, NULL, 2, NULL, 'CREDIT', 9000, 509000, '대출 1회차 상환 수취', '2026-07-25 09:00:00'),
(7, 2, NULL, NULL, 1, 'CREDIT', 50000, 100000, '카드 충전', '2026-07-28 20:00:00'),
(8, 2, 1, NULL, NULL, 'DEBIT', 3500, 96500, '편의점 결제', '2026-07-30 18:00:00');

-- T_QST_BASE_M: 부모1이 자녀2에게 퀘스트 생성
INSERT INTO `T_QST_BASE_M`
	(`id`, `parent_id`, `child_id`, `creation_request_key`, `title`, `content`, `deadline`,
	 `is_teeny_score`, `verification_requirement`, `reward_amount`, `status`)
VALUES (1, 1, 2, '00000000-0000-0000-0000-000000000001', '방 청소하기', '방과 책상을 깨끗하게 정리해 주세요.',
	'2026-08-05 23:59:59', TRUE, 'PHOTO_REQUIRED', 1000, 'IN_PROGRESS');

-- T_QST_VERIFY_L: 자녀2가 인증 제출
INSERT INTO `T_QST_VERIFY_L` (`id`, `quest_id`, `attempt_no`, `image_key`, `content`, `status`)
VALUES (1, 1, 1, 'quest-verifications/1/seed.jpg', '깨끗하게 치웠어요!', 'PENDING');

-- T_TNY_SCOREHIST_H: 적금 1회차 납입 성공으로 자녀2 점수 +10 (600 -> 610)
--
-- 이 행을 퀘스트에 걸지 않는 이유: 위 퀘스트 1은 아직 IN_PROGRESS 이고 인증도 PENDING 이다.
-- 승인되지 않은 퀘스트에 보상 점수가 이미 지급된 상태로 두면, 퀘스트 승인 로직을 이 시드로
-- 테스트할 때 점수가 두 번 올라가는 것을 정상으로 착각하게 된다. 퀘스트 1은 '승인 대기' 케이스로 남긴다.
-- score_after 610 은 T_MBR_INFO_M 의 자녀2 teeny_score 와 반드시 같아야 한다.
--
-- [V004] event_code / event_key 는 NOT NULL 이므로 생략할 수 없다.
-- description('적금납입성공')은 화면 문구라 바뀔 수 있고, event_code 는 조회·정책 판정에 쓰는
-- 고정 식별값이다. event_key 는 V004 주석의 형식(가입번호:회차)을 따라 적금 가입 1번의
-- 1회차 납입을 가리키며, UQ_TNY_SCOREHIST_H_CHILD_EVENT_KEY 가 자녀별 중복 반영을 막는다.
-- 여기에 'LEGACY' 를 쓰면 안 된다. LEGACY 는 V004 가 원인을 알 수 없는 과거 행을 채운 값이다.
INSERT INTO `T_TNY_SCOREHIST_H` (`id`, `child_id`, `amount`, `score_after`, `event_code`, `event_key`, `description`, `reference_type`, `reference_id`, `created_at`)
VALUES (1, 2, 10, 610, 'SAVING_FIXED_INSTALLMENT_PAID', 'SAVING_FIXED_PAID:1:1', '적금납입성공', 'SAVING_ENROLLMENT', 1, '2026-06-25 09:00:00');

-- T_TDP_REQ_L + T_TDP_REQCTGR_R
--
-- expired_at 은 컬럼 주석대로 '당일 자정'이라 실행 시점 기준으로 계산한다. 고정 날짜를 박으면
-- 날이 지나는 순간 "이미 만료됐는데 status 는 PENDING" 인 행이 되고, 스키마에 그 상태를 위한
-- EXPIRED 가 따로 있으므로 만료 배치가 닫았어야 할 행을 대기 목록 조회가 계속 집어 온다.
INSERT INTO `T_TDP_REQ_L` (`id`, `parent_id`, `child_id`, `reason`, `status`, `expired_at`)
VALUES (1, 1, 3, '친구 생일선물 사려고요', 'PENDING', TIMESTAMP(CURDATE(), '23:59:59'));

-- 요청 대상은 PC방·노래방이다. 편의점은 default_policy 가 ALLOW 라 승인해도 바뀌는 게 없어
-- 이 기능의 목적(WATCH/BLOCK 을 하루만 푸는 것)을 시드로 검증할 수 없다.
-- 자녀3은 T_MCC_POLICY_M 에 커스텀 정책이 없어 PC방·노래방에 기본값 WATCH 가 적용된 상태다.
INSERT INTO `T_TDP_REQCTGR_R` (`today_permission_id`, `merchant_category_id`)
VALUES (1,
        (SELECT `id` FROM `T_MCC_CTGR_C` WHERE `name` = 'PC방·노래방'));

-- T_ALW_SCHEDULE_M: 부모1 -> 자녀3, 매달 5일 지급
INSERT INTO `T_ALW_SCHEDULE_M` (`id`, `parent_id`, `child_id`, `amount`, `cycle_type`, `payment_day`, `next_payment_date`)
VALUES (1, 1, 3, 20000, 'MONTHLY', 5, '2026-08-05');

-- T_NTF_NOTI_L: 위 결제(payment 1) 직후 부모에게 나간 알림
INSERT INTO `T_NTF_NOTI_L` (`id`, `member_id`, `title`, `content`, `reference_type`, `reference_id`, `created_at`)
VALUES (1, 1, '결제 알림', '자녀2가 3,500원을 결제했어요', 'PAYMENT', 1, '2026-07-30 18:00:01');

-- ============================================================
-- 정합성 검증
--
-- 위 INSERT가 전부 성공해도 그건 제약조건을 통과했다는 뜻일 뿐이다. 제약조건은
-- "이체는 COMPLETED인데 받는 지갑 잔액은 그대로" 같은 의미적 모순을 잡지 못한다.
-- 아래 쿼리는 그런 모순만 골라 뽑는다. 결과가 Empty set 이어야 정상이다.
-- 행이 하나라도 나오면 그 데이터로 만든 테스트는 시작부터 틀린 값을 기대하게 된다.
--
-- 실행: mysql -u <user> -p <db> < sql/seed/01_seed_valid_data.sql
-- ============================================================
(SELECT '지갑 잔액이 원장 마지막 balance_after 와 다름' AS broken_rule,
        CONCAT('wallet ', w.id, ': balance=', w.balance, ', 원장=', h.balance_after) AS detail
 FROM `T_WLT_BASE_M` w
 JOIN `T_WLT_HIST_H` h ON h.wallet_id = w.id
 WHERE h.id = (SELECT MAX(h2.id) FROM `T_WLT_HIST_H` h2 WHERE h2.wallet_id = w.id)
   AND w.balance <> h.balance_after)
UNION ALL
-- 완료된 이체는 보낸 지갑 DEBIT + 받은 지갑 CREDIT 두 행을 만든다. 한쪽만 있으면 원장에 구멍이 난다.
(SELECT '완료된 이체의 원장 행이 2건이 아님',
        CONCAT('transfer ', t.id, ': 원장 ', COUNT(h.id), '건')
 FROM `T_WLT_TRF_L` t
 LEFT JOIN `T_WLT_HIST_H` h ON h.transfer_id = t.id
 WHERE t.status = 'COMPLETED'
 GROUP BY t.id
 HAVING COUNT(h.id) <> 2)
UNION ALL
-- outstanding_principal 과 paid_count 는 상환 이력을 요약한 캐시일 뿐이다.
(SELECT '대출 캐시 컬럼이 상환 이력과 불일치',
        CONCAT('loan ', l.id,
               ': outstanding=', l.outstanding_principal,
               ' 기대=', l.principal_amount - COALESCE(SUM(r.paid_principal_amount), 0),
               ', paid_count=', l.paid_count,
               ' 기대=', COALESCE(SUM(r.status = 'PAID'), 0))
 FROM `T_LON_ENROLL_M` l
 LEFT JOIN `T_LON_REPAYHIST_H` r ON r.loan_enrollment_id = l.id
 GROUP BY l.id, l.principal_amount, l.outstanding_principal, l.paid_count
 HAVING l.outstanding_principal <> l.principal_amount - COALESCE(SUM(r.paid_principal_amount), 0)
     OR l.paid_count <> COALESCE(SUM(r.status = 'PAID'), 0))
UNION ALL
-- teeny_score 도 점수 이력의 최신 score_after 를 캐시한 값이다.
(SELECT '자녀 teeny_score 가 최신 점수 이력과 다름',
        CONCAT('member ', m.id, ': teeny_score=', m.teeny_score, ', 최신 이력=', s.score_after)
 FROM `T_MBR_INFO_M` m
 JOIN `T_TNY_SCOREHIST_H` s ON s.child_id = m.id
 WHERE s.id = (SELECT MAX(s2.id) FROM `T_TNY_SCOREHIST_H` s2 WHERE s2.child_id = m.id)
   AND m.teeny_score <> s.score_after)
UNION ALL
-- 원금이 들어오지 않은 예적금은 애초에 개설될 수 없다. 이자 계산이 조용히 0을 반환한다.
(SELECT 'ACTIVE 예금인데 예치금이 0',
        CONCAT('deposit enrollment ', d.id, ', wallet ', d.wallet_id)
 FROM `T_DPT_ENROLL_M` d
 JOIN `T_WLT_BASE_M` w ON w.id = d.wallet_id
 WHERE d.status = 'ACTIVE' AND w.balance = 0)
UNION ALL
(SELECT 'PAID 납입 이력이 있는데 적금 지갑이 0',
        CONCAT('saving enrollment ', e.id, ', wallet ', e.wallet_id)
 FROM `T_SVG_ENROLL_M` e
 JOIN `T_WLT_BASE_M` w ON w.id = e.wallet_id
 WHERE w.balance = 0
   AND EXISTS (SELECT 1 FROM `T_SVG_PAYHIST_H` p
               WHERE p.saving_enrollment_id = e.id AND p.status = 'PAID'))
UNION ALL
-- 스키마에 EXPIRED 상태가 따로 있으므로, 만료 지난 PENDING 은 배치가 닫았어야 할 행이다.
(SELECT 'PENDING 인데 만료 시각이 이미 지난 오늘만허용 요청',
        CONCAT('today permission ', id, ': expired_at=', expired_at)
 FROM `T_TDP_REQ_L`
 WHERE status = 'PENDING' AND expired_at < NOW())
UNION ALL
-- 승인 전 퀘스트에 보상 점수가 이미 있으면, 승인 로직이 점수를 두 번 올려도 정상으로 보인다.
(SELECT '승인되지 않은 퀘스트에 점수 이력이 있음',
        CONCAT('quest ', q.id, ' (', q.status, '), score history ', s.id)
 FROM `T_TNY_SCOREHIST_H` s
 JOIN `T_QST_BASE_M` q ON q.id = s.reference_id
 WHERE s.reference_type = 'QUEST' AND q.status <> 'COMPLETED')
UNION ALL
-- 만기가 오지 않은 회차를 MISSED 로 두면 연체 배치가 상태를 재계산하는 순간 뒤집힌다.
(SELECT '아직 만기가 오지 않은 회차가 MISSED',
        CONCAT('saving payment ', p.id, ': ', p.installment_no, '회차 만기 ',
               DATE_ADD(e.start_date, INTERVAL (p.installment_no - 1) MONTH))
 FROM `T_SVG_PAYHIST_H` p
 JOIN `T_SVG_ENROLL_M` e ON e.id = p.saving_enrollment_id
 WHERE p.status = 'MISSED'
   AND DATE_ADD(e.start_date, INTERVAL (p.installment_no - 1) MONTH) > CURDATE())
UNION ALL
-- 필수 약관에 동의하지 않은 회원은 가입 자체가 거절되므로 존재할 수 없다.
(SELECT '필수 약관 동의 이력이 없는 회원',
        CONCAT('member ', m.id, ' → ', a.code, ' ', a.version)
 FROM `T_MBR_INFO_M` m
 CROSS JOIN `T_MBR_AGRMT_M` a
 WHERE a.is_required = TRUE
   AND NOT EXISTS (SELECT 1 FROM `T_MBR_AGRMT_H` h
                   WHERE h.member_id = m.id AND h.agreement_id = a.id AND h.status = 'AGREED'))
UNION ALL
-- 만 14세 미만은 법정대리인 인증 없이 가입할 수 없다(가입 흐름 13).
-- 동의 주체가 LEGAL_GUARDIAN 인데 근거 행이 없는 경우도 같이 잡는다.
(SELECT '만 14세 미만인데 법정대리인 인증 근거가 없음',
        CONCAT('member ', m.id, ' (', m.name, ', ', m.birth_date, ')')
 FROM `T_MBR_INFO_M` m
 WHERE m.role = 'CHILD'
   AND DATE_ADD(m.birth_date, INTERVAL 14 YEAR) > CURDATE()
   AND NOT EXISTS (SELECT 1 FROM `T_MBR_LEGAL_GUARDIAN_M` g WHERE g.child_member_id = m.id));
