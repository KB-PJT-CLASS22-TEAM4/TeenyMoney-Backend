-- 마감 배치 통합 테스트가 만든 퀘스트만 지운다.
-- 인증 이력은 FK 의 ON DELETE CASCADE 로 함께 정리된다.
DELETE FROM `T_QST_BASE_M` WHERE `id` BETWEEN 900000 AND 900999;
