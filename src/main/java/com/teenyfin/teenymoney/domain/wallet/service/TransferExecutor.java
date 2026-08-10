package com.teenyfin.teenymoney.domain.wallet.service;


import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.TransferMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.ReferenceType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 송금 행을 잠근 채로 실제 잔액을 옮기는 부분만 전담한다. TransferService에서 이 로직을
// 분리해낸 이유: 이 메서드가 실패로 끝나면(예외로 메서드를 빠져나가면) 스프링이 이 메서드의
// @Transactional 전체를 즉시 롤백시키고, 그 순간 selectForUpdate로 잡은 락도 함께 풀린다.
// 이 로직이 TransferService.executeTransfer() 안에 같이 있으면, 실패 처리(TransferFailureRecorder.
// markFailed())를 부르는 시점에도 이 락이 아직 안 풀린 채로 남아있어서, REQUIRES_NEW로 새로 여는
// 커넥션이 같은 행을 UPDATE하려다 자기 자신(같은 스레드가 만든 다른 커넥션)을 기다리는 교착
// 상태가 생긴다 - 그래서 "락을 잡는 트랜잭션"과 "실패를 기록하는 트랜잭션"을 별도 빈으로
// 완전히 분리해서, 앞쪽이 먼저 롤백을 끝내고 락을 놓은 뒤에야 뒤쪽이 실행되게 만든다.
@Service
public class TransferExecutor {

    private final TransferMapper transferMapper;
    private final WalletLedgerService walletLedgerService;

    public TransferExecutor(TransferMapper transferMapper, WalletLedgerService walletLedgerService) {
        this.transferMapper = transferMapper;
        this.walletLedgerService = walletLedgerService;
    }

    // 송금 행을 잠근 채로 조회하고, 아직 안 끝났으면 실제로 잔액을 옮기고 COMPLETED로 표시한다.
    // 실패하면(잔액부족이든 그 외 오류든) 여기서 잡지 않고 그냥 던진다 - 그래야 스프링이 이
    // 메서드 전체를 롤백시키고, "실패를 어떻게 기록할지"는 전혀 모른 채로 호출한 쪽(TransferService)
    // 에게 그 판단을 완전히 넘긴다.

    @Transactional
    public TransferVO lockAndMove(Long transferId) {
        TransferVO current = transferMapper.selectForUpdate(transferId);
        if (current == null) {
            throw new BusinessException(WalletErrorCode.TRANSFER_NOT_FOUND);
        }

        // 이미 COMPLETED된 걸 또 실행하면 잔액을 두 번 옮기게 되는 대참사가 벌어진다.
        // 동시에 같은 transferId를 처리하려는 두 번째 호출은 위 selectForUpdate에서
        // 첫 번째가 커밋할 때까지 대기하다가, 대기가 풀리면 이 최신 상태를 보고 여기서 멈춘다.
        if ("COMPLETED".equals(current.getStatus())) {
            return current;
        }

        // 잠금 순서 규칙: 두 지갑을 잠글 때 항상 "wallet_id가 작은 쪽부터" 잠가야 한다.
        // 이유: A->B 송금과 B->A 송금이 동시에 일어난다고 하면,
        //   - "from을 먼저 잠근다"는 규칙만 따르면: A->B는 A를 먼저 잠그고, B->A는 B를 먼저 잠근다.
        //     이러면 각자 상대방이 이미 잠근 지갑을 서로 기다리게 되는 데드락이 발생할 수 있다.
        //   - 대신 "숫자가 작은 wallet_id부터 잠근다"는 규칙을 걸어두면, A->B든 B->A든
        //     항상 같은 순서(작은 id부터)로 잠그니까 서로 물리는 상황 자체가 생기지 않는다.
        // debit()/credit() 각각이 내부에서 SELECT ... FOR UPDATE로 잠그기 때문에,
        // 여기서 "어느 걸 먼저 호출하느냐"로 잠금 순서를 강제한다.

        String label = TransferType.valueOf(current.getType()).getLabel();
        if (current.getFromWalletId() < current.getToWalletId()) {
            walletLedgerService.debit(current.getFromWalletId(), current.getAmount(), ReferenceType.TRANSFER, current.getId(),label + " 출금");
            walletLedgerService.credit(current.getToWalletId(), current.getAmount(), ReferenceType.TRANSFER, current.getId(), label + " 입금");
        } else {
            walletLedgerService.credit(current.getToWalletId(), current.getAmount(), ReferenceType.TRANSFER, current.getId(), label + " 입금");
            walletLedgerService.debit(current.getFromWalletId(), current.getAmount(), ReferenceType.TRANSFER, current.getId(),label + " 출금");
        }

        transferMapper.updateStatus(current.getId(), "COMPLETED", null);
        current.setStatus("COMPLETED");
        return current;
    }

}
