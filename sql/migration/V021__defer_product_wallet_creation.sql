-- 예·적금 상품 지갑은 가입 요청이 아니라 부모 승인 성공 시 생성한다.
ALTER TABLE `T_DPT_ENROLL_M`
    MODIFY COLUMN `wallet_id` BIGINT NULL
    COMMENT '승인 후 생성되는 예치금 관리 지갑 아이디 (UNIQUE)';

ALTER TABLE `T_SVG_ENROLL_M`
    MODIFY COLUMN `wallet_id` BIGINT NULL
    COMMENT '승인 후 생성되는 누적 납입액 관리 지갑 아이디 (UNIQUE)';
