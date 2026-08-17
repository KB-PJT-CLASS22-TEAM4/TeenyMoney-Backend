package com.teenyfin.teenymoney.domain.wallet.mapper;

import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TransferMapper {

    //idempotency_key 로 기존 송금 시도 찾기 없으면 null 리턴
    // idempotency_key로 기존 송금 시도를 찾는다. 없으면 null 리턴.
    // TransferService.createPendingTransfer()가 "이 키로 이미 만든 행이 있는지"부터
    // 먼저 확인할 때 쓴다 - 같은 요청이 중복으로 와도 송금이 두 번 실행되지 않게 막는 용도.
    TransferVO selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    // T_WLT_TRF_L에 PENDING 상태의 송금 행 1건을 새로 만든다.
    // 파라미터 타입이 TransferVO 통째인 이유: 채워야 할 컬럼(from/to walletId, amount, type,
    // idempotencyKey)이 4개나 되는데, WalletMapper.insertWallet(WalletVO wallet)처럼
    // 객체 하나로 묶어서 넘기는 게 @Param을 4개 나열하는 것보다 깔끔하기 때문



    // 리턴 타입은 void지만, 실행 후 transfer.getId()에 방금 생성된 PK가 자동으로 채워진다
    // (XML의 useGeneratedKeys="true" keyProperty="id" 덕분 - insertWallet과 같은 방식).
    // status는 여기서 안 채워도 된다 - DB 컬럼에 DEFAULT 'PENDING'이 걸려있어서
    // INSERT 시점에 DB가 알아서 'PENDING'을 넣어준다.'
    void insertTransfer(TransferVO transfer);

    // 송금 행의 상태를 갱신한다 (PENDING -> COMPLETED 또는 PENDING -> FAILED).
    // failureReason은 성공(COMPLETED) 시엔 null로 넘기면 된다 (컬럼이 NULL 허용).
    // id로 갱신 대상을 찍는 이유: TransferService.executeTransfer()가 이미
    // createPendingTransfer()에서 만들어진 TransferVO(= id가 이미 있는 상태)를 받아서
    // 그 행 하나만 정확히 업데이트해야 하기 때문.
    void updateStatus(@Param("id") Long id, @Param("status") String status, @Param("failureReason") String failureReason);

    // 송금 행을 "잠근 채로" 조회한다 (SELECT ... FOR UPDATE) - WalletMapper.selectWalletForUpdate와 같은 방식.
    // executeTransfer()가 재처리 여부를 판단할 때, 호출한 쪽이 들고 있는(어쩌면 오래된) pending 객체의
    // status를 믿지 않고, 이걸로 DB에서 "지금 이 순간의 진짜 상태"를 다시 확인하기 위해 쓴다.
    // FOR UPDATE 잠금 덕분에, 같은 id를 동시에 처리하려는 두 번째 호출은 첫 번째가 커밋할 때까지
    // 이 SELECT 자체에서 대기하게 된다 - 그래서 대기가 풀린 뒤엔 항상 최신 상태를 보게 된다.
    TransferVO selectForUpdate(@Param("id") Long id);



    // 송금 행을 FAILED로 표시한다. status <> 'COMPLETED'일 때만 적용되도록 XML에서 조건을 건다 -
    // 이미 다른 스레드가 COMPLETED로 확정지은 행을, 뒤늦게 도착한 실패 처리가 덮어쓰지 못하게
    // 막기 위해서다 (TransferExecutor가 실패로 롤백하며 락을 놓은 직후, 다른 재시도가 먼저
    // 성공시켜버리는 아주 좁은 틈이 이론상 있을 수 있다 - 이 조건이 그 경우를 안전하게 무시한다).
    // PENDING/FAILED 상태에서는 그대로 적용되므로 "실패한 송금을 나중에 재시도"하는 흐름은 안 깨진다.
    void markFailed(@Param("id") Long id, @Param("failureReason") String failureReason);

    // id로 송금 행을 잠금 없이 그냥 조회
    TransferVO selectById(@Param("id") Long id);
}
