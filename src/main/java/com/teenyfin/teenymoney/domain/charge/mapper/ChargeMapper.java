package com.teenyfin.teenymoney.domain.charge.mapper;

import com.teenyfin.teenymoney.domain.charge.vo.ChargeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;



@Mapper
public interface ChargeMapper {

    // idempotency_key로 기존 충전 시도를 찾는다. 없으면 null.
    // TransferMapper.selectByIdempotencyKey와 같은 이유 - 같은 요청이 중복으로 와도
    // createPendingCharge()가 새 행을 또 안 만들게 막는 용도.
    ChargeVO selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    // T_WLT_CHARGE_L에 PENDING 상태의 충전 행 1건을 새로 만든다.
    // 리턴 타입은 void지만, 실행 후 charge.getId()에 생성된 PK가 자동으로 채워진다
    // (XML의 useGeneratedKeys="true" keyProperty="id" 덕분).
    void insertCharge(ChargeVO charge);

    // 락 없는 단순 조회. executeCharge() 맨 앞에서 "이미 SUCCESS인지" 가볍게 먼저
    // 확인할 때 쓴다 - 아직 토스를 부르기도 전이라 굳이 행을 잠글 필요가 없다.
    ChargeVO selectById(@Param("id") Long id);

    // 잠근 채로 조회한다 (SELECT ... FOR UPDATE). ChargeExecutor.lockAndCredit()에서
    // "SUCCESS 마킹 + credit()"을 한 트랜잭션으로 원자적으로 처리할 때 쓴다.
    ChargeVO selectForUpdate(@Param("id") Long id);

    // 토스를 부르기 직전에 PENDING -> PROCESSING으로 선점한다.
    // 리턴값은 이 UPDATE가 실제로 몇 행에 영향을 줬는지(0 또는 1) - MyBatis가
    // update 문의 영향받은 행 수를 자동으로 int로 돌려준다. 0이면 이미 다른 요청이
    // 선점했다는 뜻이라, executeCharge()가 여기서 중단해야 한다.
    int claimForProcessing(@Param("id") Long id);

    // 토스 승인 성공 후 SUCCESS로 확정하면서 paymentKey를 같이 저장한다.
    // paymentKey는 나중에 취소(환불) API에서 "어떤 결제를 취소할지" 지정하는 데 쓰인다.
    void markSuccess(@Param("id") Long id, @Param("paymentKey") String paymentKey);

    // 실패 사유를 남기며 FAILED로 표시한다. TransferMapper.markFailed와 같은 이유로
    // 이미 SUCCESS로 확정된 행은 절대 덮어쓰지 않는다 (XML에서 조건으로 막음).
    void markFailed(@Param("id") Long id, @Param("failureReason") String failureReason);

    // 토스와 통신 자체가 실패했거나(타임아웃 등), 토스가 "아직 처리 중"이라고 응답한 경우
    // PROCESSING을 PENDING으로 되돌린다. 이래야 다음 재시도가 claimForProcessing()으로
    // 다시 선점하고, 같은 orderId+Idempotency-Key로 토스를 다시 부를 수 있다.
    // (되돌리지 않으면 claimForProcessing()이 PENDING일 때만 통과시켜서 재시도 자체가 영원히 막힘.)
    // 이미 SUCCESS/FAILED로 확정된 행은 절대 되돌리지 않는다.
    void revertToPending(@Param("id") Long id);
}

