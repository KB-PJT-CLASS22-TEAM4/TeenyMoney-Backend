package com.teenyfin.teenymoney.domain.wallet.vo;


// T_WLT_BASE_M.type 컬럼(DB CHECK 제약: MEMBER/DEPOSIT/SAVING 셋 중 하나)을
// 자바 코드에서 문자열 대신 enum으로 다루기 위한 타입.
// MEMBER = 회원 본인 지갑(회원가입 시 1개), DEPOSIT = 예금 가입 건별 예치금 지갑,
// SAVING = 적금 가입 건별 납입액 지갑 (예적금은 가입 건마다 새로 생김 -> 한 회원이 여러 개 가질 수 있음)
public enum WalletType {
    MEMBER, DEPOSIT, SAVING
}
