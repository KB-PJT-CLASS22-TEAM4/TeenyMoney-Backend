-- 퀘스트 승인 시 부모 지갑에서 자녀 지갑으로 지급하는 보상 송금 유형을 추가한다.
ALTER TABLE `T_WLT_TRF_L`
    DROP CHECK `CK_WLT_TRF_L_TYPE`;

ALTER TABLE `T_WLT_TRF_L`
    ADD CONSTRAINT `CK_WLT_TRF_L_TYPE`
    CHECK (`type` IN (
        'ALLOWANCE',
        'DEPOSIT',
        'SAVING',
        'LOAN',
        'QUEST_REWARD',
        'TRANSFER'
    ));

ALTER TABLE `T_WLT_TRF_L`
    MODIFY `type` VARCHAR(50) NOT NULL
    COMMENT '유형: ALLOWANCE(용돈) / DEPOSIT(예금) / SAVING(적금) / LOAN(대출) / QUEST_REWARD(퀘스트 보상) / TRANSFER(일반송금) (CHECK)';
