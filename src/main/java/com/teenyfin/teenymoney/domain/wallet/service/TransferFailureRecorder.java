package com.teenyfin.teenymoney.domain.wallet.service;

import com.teenyfin.teenymoney.domain.wallet.mapper.TransferMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferFailureRecorder {
    // 딱 하나의 책임만 진다: 송금 실패 사유를 "무슨 일이 있어도 독립적으로" 남긴다.
    // TransferService.executeTransfer()가 debit()/credit() 실패로 롤백되는 트랜잭션과
    // 이 클래스의 트랜잭션이 물리적으로 분리되어야 하므로, 반드시 TransferService와는
    // 다른 스프링 빈(다른 클래스)이어야 한다 - 같은 클래스 안의 메서드였다면
    // this.markFailed(...) 호출이 프록시를 안 거쳐서(self-invocation) 아래 @Transactional이
    // 무시된다.

    private final TransferMapper transferMapper;

    public TransferFailureRecorder(TransferMapper transferMapper) {
        this.transferMapper = transferMapper;
    }
    // propagation = REQUIRES_NEW: "지금 이 메서드를 호출한 쪽에 이미 열려있는 트랜잭션이
    // 있더라도, 그건 무시하고 완전히 새로운(독립된) 트랜잭션을 하나 더 연다"는 뜻.
    // 이 메서드 안에서 커밋되면 그 즉시 확정되고, 나중에 호출한 쪽(TransferService)의
    // 트랜잭션이 롤백되더라도 이 커밋은 절대 같이 취소되지 않는다.
    // (기본값인 REQUIRED였다면 호출한 쪽 트랜잭션에 그냥 합류해버려서, 결국 지금
    //  TransferService에서 겪고 있는 문제가 그대로 재현됐을 것 - 그래서 REQUIRES_NEW가 핵심.)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long transferId, String reason) {
        transferMapper.markFailed(transferId, reason);
    }
}
