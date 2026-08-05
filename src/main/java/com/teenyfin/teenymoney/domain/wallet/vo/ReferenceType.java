package com.teenyfin.teenymoney.domain.wallet.vo;



// T_WLT_HIST_H(지갑 변경 내역) 테이블엔 payment_id/transfer_id/charge_id 세 컬럼이 있는데
// 한 거래당 이 중 딱 하나만 값이 채워지고 나머지 둘은 NULL이어야 한다(DB CHECK 제약).
// 이 enum은 "이번 거래가 셋 중 뭐냐"를 WalletLedgerService에게 알려주는 용도.
// PAYMENT = 결제(T_PAY_TRAN_L), TRANSFER = 송금(T_WLT_TRF_L), CHARGE = 충전(T_WLT_CHARGE_L)
public enum ReferenceType {
    PAYMENT, TRANSFER, CHARGE


}
