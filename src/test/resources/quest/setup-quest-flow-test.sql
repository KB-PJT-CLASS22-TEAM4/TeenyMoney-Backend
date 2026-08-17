-- 퀘스트 전체 흐름 통합 테스트용 픽스처.
--
-- 마감 배치 픽스처(setup-quest-deadline-test.sql)와 달리 회원 ID 가 양수다.
-- 퀘스트 생성 API 가 childIds 를 양수로 검증하기 때문에 음수 회원으로는 생성 단계를
-- 통과할 수 없다. 대신 seed 와 절대 겹치지 않는 900000 번대를 쓰고, 검증을 거치지 않는
-- 지갑·연동 ID 는 음수로 남겨 마감 픽스처와 같은 대역 규칙을 지킨다.
-- 900000 번대를 INSERT 하면 T_MBR_INFO_M 의 AUTO_INCREMENT 가 그 위로 올라간다.
-- BIGINT 라 번호가 뛰는 것 말고는 영향이 없다.
--
-- 퀘스트 행은 서비스가 직접 만든다. 여기서 미리 넣지 않는 이유는, 이 테스트가 검증하려는
-- 것이 "부모 생성부터 보상까지"의 실제 경로이기 때문이다. 퀘스트를 손으로 넣으면
-- 생성 단계를 건너뛰게 된다.
--
-- 시각 기준은 테스트 컨텍스트의 고정 시계(2000-01-02 10:00 KST)다. 마감 배치가 이 테스트의
-- 퀘스트만 집도록 기한도 2000년대로 잡는다. 운영·개발 DB 의 실제 퀘스트는 훨씬 미래라
-- closeExpired() 가 건드리지 않는다.

DELETE FROM `T_NTF_NOTI_L` WHERE `member_id` IN (900011, 900012);
DELETE FROM `T_TNY_SCOREHIST_H` WHERE `child_id` = 900012;
DELETE FROM `T_WLT_HIST_H` WHERE `wallet_id` IN (-900011, -900012);
DELETE FROM `T_WLT_TRF_L`
WHERE `from_wallet_id` IN (-900011, -900012)
   OR `to_wallet_id` IN (-900011, -900012);
DELETE FROM `T_QST_VERIFY_L`
WHERE `quest_id` IN (SELECT `id` FROM `T_QST_BASE_M` WHERE `parent_id` = 900011);
DELETE FROM `T_QST_BASE_M` WHERE `parent_id` = 900011;
DELETE FROM `T_WLT_BASE_M` WHERE `member_id` IN (900011, 900012);
DELETE FROM `T_MBR_CONN_R` WHERE `parent_id` = 900011;
DELETE FROM `T_MBR_INFO_M` WHERE `id` IN (900011, 900012);

INSERT INTO `T_MBR_INFO_M`
(`id`, `role`, `name`, `birth_date`, `phone_number`, `email`, `password`, `teeny_score`, `status`)
VALUES
(900011, 'PARENT', '흐름테스트부모', '1985-01-01', '01090000011',
 'quest-flow-parent@test.local', 'test-password', NULL, 'ACTIVE'),
(900012, 'CHILD', '흐름테스트자녀', '2013-01-01', '01090000012',
 'quest-flow-child@test.local', 'test-password', 600, 'ACTIVE');

-- 부모 권한 검사(FamilyAccessService)가 ACTIVE 연동 한 건을 요구한다.
INSERT INTO `T_MBR_CONN_R` (`id`, `parent_id`, `child_id`, `status`, `created_at`)
VALUES (-900011, 900011, 900012, 'ACTIVE', '2000-01-01 09:00:00');

-- 보상 지급은 두 지갑 사이의 실제 송금이다. 부모 잔액 5,000원으로 시작한다.
-- 잔액 부족 시나리오는 이 금액보다 큰 보상을 건 퀘스트로 만든다.
INSERT INTO `T_WLT_BASE_M` (`id`, `member_id`, `balance`, `type`, `created_at`)
VALUES
(-900011, 900011, 5000, 'MEMBER', '2000-01-01 09:00:00'),
(-900012, 900012, 0, 'MEMBER', '2000-01-01 09:00:00');
