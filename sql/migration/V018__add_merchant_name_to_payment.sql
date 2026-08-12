-- 결제(T_PAY_TRAN_L)에 가맹점명(merchant_name) 컬럼을 추가한다.
--
-- QR 결제 흐름에서 가맹점명은 Redis(OrderStore)에만 임시로 저장되고,
-- 결제 확정 시점에는 DB에 남지 않았다. 그 결과 두 가지 문제가 있었다.
--   1) 지갑 변동 내역(T_WLT_HIST_H.description)에는 가맹점명이 남지만,
--      결제 내역(T_PAY_TRAN_L) 자체에는 남지 않아 결제 단건 조회만으로는
--      어느 가맹점의 거래인지 알 수 없다.
--   2) 멱등성 키 재요청 시 Redis의 주문 정보가 TTL 만료로 사라진 경우,
--      DB에 저장된 기존 결제 건만으로 응답을 재구성해야 하는데
--      가맹점명을 채울 방법이 없어 결제 응답이 불완전해진다.
--
-- 실제 결제창 노출용 프로퍼티가 아니라 거래 스냅샷 성격이라 NULL을 허용한다.
ALTER TABLE `T_PAY_TRAN_L`
    ADD COLUMN `merchant_name` VARCHAR(100) NULL
        COMMENT '결제 시점 가맹점명 스냅샷 (QR 원본 값, 재조회/재요청 응답 구성용)';