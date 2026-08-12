package com.teenyfin.teenymoney.domain.charge.service;


import com.teenyfin.teenymoney.domain.charge.exception.ChargeErrorCode;
import com.teenyfin.teenymoney.domain.charge.mapper.ChargeMapper;
import com.teenyfin.teenymoney.domain.charge.vo.ChargeVO;
import com.teenyfin.teenymoney.domain.wallet.service.WalletLedgerService;
import com.teenyfin.teenymoney.domain.wallet.vo.ReferenceType;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 충전 행을 잠근 채로 "SUCCESS 확정 + 지갑 크레딧"을 원자적으로 처리하는 부분만 전담한다.
// TransferExecutor.lockAndMove()와 같은 이유로 ChargeService에서 이 로직을 분리해뒀다 -
// lockAndCredit()이 실패로 끝나면 스프링이 이 메서드의 @Transactional 전체를 롤백시키고
// 그 순간 락도 같이 풀리는데, 이 로직이 ChargeService 안에 섞여 있으면 실패 처리
// (ChargeFailureRecorder.markFailed())를 부르는 시점에 아직 락이 안 풀린 채로 남아있어서
// REQUIRES_NEW로 새로 여는 커넥션이 같은 행을 기다리다 교착 상태에 빠질 수 있다.
// 그래서 "락을 잡는 트랜잭션"(여기)과 "그걸 부르는 오케스트레이션"(ChargeService, 트랜잭션 없음)을
// 완전히 별도 빈으로 분리한다.

@Service
public class ChargeExecutor {


    private final ChargeMapper chargeMapper;
    private final WalletLedgerService walletLedgerService;

    public ChargeExecutor(ChargeMapper chargeMapper, WalletLedgerService walletLedgerService) {
        this.chargeMapper = chargeMapper;
        this.walletLedgerService = walletLedgerService;
    }

    // 충전 행을 잠근 채로 조회하고, 아직 안 끝났으면 SUCCESS로 확정 + 지갑에 크레딧한다.
    // 실패하면(지갑 없음 등) 여기서 안 잡고 그냥 던진다 - 그래야 스프링이 이 메서드
    // 전체를 롤백시키고, "실패를 어떻게 기록할지"는 전혀 모른 채로 호출한 쪽
    // (ChargeService)에게 그 판단을 완전히 넘긴다.
    @Transactional
    public ChargeVO lockAndCredit(Long chargeId, String paymentKey) {
        ChargeVO current = chargeMapper.selectForUpdate(chargeId);
        if (current == null) {
            throw new BusinessException(ChargeErrorCode.CHARGE_NOT_FOUND);
        }

        // 이미 SUCCESS로 확정된 걸 또 실행하면 지갑에 돈을 두 번 넣는 대참사가 벌어진다.
        // 동시에 같은 chargeId를 처리하려는 두 번째 호출은 위 selectForUpdate에서
        // 첫 번째가 커밋할 때까지 대기하다가, 풀리면 이 최신 상태를 보고 여기서 멈춘다.
        if ("SUCCESS".equals(current.getStatus())) {
            return current;
        }

        // 순서: SUCCESS 마킹 먼저, 그다음 크레딧. 둘 다 같은 트랜잭션 안이라 원자적으로
        // 묶이므로 순서 자체는 중요하지 않지만, "이 충전이 성공했다"는 사실을 먼저
        // 확정해두는 게 논리적으로 자연스럽다.
        chargeMapper.markSuccess(chargeId, paymentKey);
        // WalletLedgerService.credit()이 내부적으로 지갑을 잠그고, 잔액을 올리고,
        // T_WLT_HIST_H에 원장 한 줄을 같이 남긴다 (잔액 갱신 + 내역 생성이 이 한 줄로 끝남).
        walletLedgerService.credit(
                current.getWalletId(), current.getAmount(), ReferenceType.CHARGE, current.getId(), "지갑 충전");

        current.setStatus("SUCCESS");
        current.setPaymentKey(paymentKey);
        return current;
    }

}
