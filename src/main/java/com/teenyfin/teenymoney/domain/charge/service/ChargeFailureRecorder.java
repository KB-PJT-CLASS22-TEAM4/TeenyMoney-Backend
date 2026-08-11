package com.teenyfin.teenymoney.domain.charge.service;

import com.teenyfin.teenymoney.domain.charge.mapper.ChargeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 딱 하나의 책임만 진다: 충전 실패 사유를 "무슨 일이 있어도 독립적으로" 남긴다.
// TransferFailureRecorder와 똑같은 이유로, ChargeService/ChargeExecutor의 실패로
// 롤백되는 트랜잭션과 이 클래스의 트랜잭션이 물리적으로 분리되어야 하므로,
// 반드시 다른 스프링 빈(다른 클래스)이어야 한다.
@Service
public class ChargeFailureRecorder {

    private final ChargeMapper chargeMapper;

    public ChargeFailureRecorder(ChargeMapper chargeMapper) {
        this.chargeMapper = chargeMapper;
    }

    // propagation = REQUIRES_NEW: "지금 이 메서드를 호출한 쪽에 이미 열려있는 트랜잭션이
    // 있더라도, 그건 무시하고 완전히 새로운(독립된) 트랜잭션을 하나 더 연다"는 뜻.
    // 이 메서드 안에서 커밋되면 그 즉시 확정되고, 나중에 호출한 쪽(ChargeService)의
    // 트랜잭션이 롤백되더라도 이 커밋은 절대 같이 취소되지 않는다.
    // (기본값인 REQUIRED였다면 호출한 쪽 트랜잭션에 그냥 합류해버려서, 호출한 쪽이
    //  롤백될 때 이 실패 기록도 같이 날아가버리는 문제가 생김 - 그래서 REQUIRES_NEW가 핵심.)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long chargeId, String reason) {
        chargeMapper.markFailed(chargeId, reason);
    }
}
