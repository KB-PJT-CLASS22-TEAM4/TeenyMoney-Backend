package com.teenyfin.teenymoney.domain.wallet.dto.request;


// "전체/입금/출금" 버튼 값을 표현하는 enum.
// 프론트가 GET /wallet/me/transactions?type=CREDIT 이런 식으로 쿼리 파라미터를 보내면,
// Spring이 자동으로 이 문자열("CREDIT")을 TransactionTypeFilter.CREDIT으로 변환해준다.
// (Spring MVC는 @RequestParam이 enum 타입이면 내부적으로 Enum.valueOf()를 호출함)
public enum TransactionTypeFilter {
    ALL, CREDIT, DEBIT

}
