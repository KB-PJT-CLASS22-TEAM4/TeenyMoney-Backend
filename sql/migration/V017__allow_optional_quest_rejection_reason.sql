-- 퀘스트 인증 반려 사유를 선택 사항으로 변경한다.
ALTER TABLE `T_QST_VERIFY_L`
    DROP CHECK `CK_QST_VERIFY_L_REJECTION_REASON`;

ALTER TABLE `T_QST_VERIFY_L`
    ADD CONSTRAINT `CK_QST_VERIFY_L_REJECTION_REASON`
        CHECK (
            `status` <> 'REJECTED'
                OR `rejection_reason` IS NULL
                OR CHAR_LENGTH(TRIM(`rejection_reason`)) BETWEEN 1 AND 500
            );