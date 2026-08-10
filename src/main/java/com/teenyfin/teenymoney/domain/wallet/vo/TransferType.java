package com.teenyfin.teenymoney.domain.wallet.vo;


// T_WLT_TRF_L.type 컬럼에 들어가는 값이자, 지갑 원장(T_WLT_HIST_H.description)에
// 표시할 기본 라벨도 함께 들고 있는 enum. 새 송금 종류가 생기면(예: 적금) 여기
// 한 줄만 추가하면 TransferExecutor가 알아서 그 라벨로 "OO 출금"/"OO 입금"을 만든다.
public enum TransferType {
    ALLOWANCE("용돈"),
    DEPOSIT("예금"),
    SAVING("적금"),
    LOAN("대출"),
    TRANSFER("송금");

    // enum 상수마다 딸려오는 값 하나. ALLOWANCE("용돈")처럼 생성자 호출하듯 써주면,
    // TransferType.ALLOWANCE.getLabel()이 "용돈"을 돌려준다.
    private final String label;

    TransferType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
