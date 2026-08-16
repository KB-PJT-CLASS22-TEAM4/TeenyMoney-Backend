-- 승인 전 상품 지갑과 PENDING 송금을 만들지 않아도 예금 신청 금액을 보존한다.
ALTER TABLE `T_DPT_ENROLL_M`
    ADD COLUMN `deposit_amount` BIGINT NULL
    COMMENT '가입 신청 예금액 스냅샷'
    AFTER `term_months`;

ALTER TABLE `T_DPT_ENROLL_M`
    ADD CONSTRAINT `CK_DPT_ENROLL_M_DEPOSIT_AMOUNT`
    CHECK (`deposit_amount` IS NULL OR `deposit_amount` > 0);

-- 구버전 가입은 신청 금액을 별도 컬럼 대신 예금 송금에 저장했다.
-- 기존 PENDING/ACTIVE 데이터가 새 승인·정산 코드에서도 동작하도록 그 값을 이관한다.
UPDATE `T_DPT_ENROLL_M` enrollment
INNER JOIN `T_WLT_TRF_L` transfer
        ON transfer.to_wallet_id = enrollment.wallet_id
       AND transfer.type = 'DEPOSIT'
SET enrollment.deposit_amount = transfer.amount
-- MySQL Workbench의 Safe Update Mode에서도 실행되도록 PK 범위 조건을 명시한다.
WHERE enrollment.id > 0
  AND enrollment.deposit_amount IS NULL;
