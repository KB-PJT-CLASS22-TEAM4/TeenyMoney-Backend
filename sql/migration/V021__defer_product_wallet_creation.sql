-- 예·적금 상품 지갑은 가입 요청이 아니라 부모 승인 성공 시 생성한다.
ALTER TABLE `T_DPT_ENROLL_M`
    MODIFY COLUMN `wallet_id` BIGINT NULL
    COMMENT '승인 후 생성되는 예치금 관리 지갑 아이디 (UNIQUE)';

ALTER TABLE `T_SVG_ENROLL_M`
    MODIFY COLUMN `wallet_id` BIGINT NULL
    COMMENT '승인 후 생성되는 누적 납입액 관리 지갑 아이디 (UNIQUE)';

-- 만기 미완납 대출도 이후 자동상환 대상에 남겨 두기 위한 상태를 허용한다.
ALTER TABLE `T_LON_ENROLL_M`
    DROP CHECK `CK_LON_ENROLL_M_STATUS`;

ALTER TABLE `T_LON_ENROLL_M`
    ADD CONSTRAINT `CK_LON_ENROLL_M_STATUS`
    CHECK (`status` IN ('PENDING', 'REJECTED', 'ACTIVE', 'OVERDUE', 'DEFAULTED', 'REPAID'));
