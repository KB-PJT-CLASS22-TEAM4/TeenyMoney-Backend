package com.teenyfin.teenymoney.domain.payment.service;

import com.teenyfin.teenymoney.domain.payment.mapper.MemberPaymentMapper;
import com.teenyfin.teenymoney.domain.payment.vo.MemberPaymentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberPaymentService {

    private final MemberPaymentMapper memberPaymentMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int incrementFailedCountAndGet(Long memberId) {
        memberPaymentMapper.incrementPaymentPasswordFailedCount(memberId);
        MemberPaymentVO updated = memberPaymentMapper.selectByMemberId(memberId);

        return updated.getPaymentPasswordFailedCount();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePaymentLockedUntil(Long memberId, LocalDateTime lockedUntil) {
        memberPaymentMapper.updatePaymentLockedUntil(memberId, lockedUntil);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetPaymentPasswordFailedCount(Long memberId) {
        memberPaymentMapper.resetPaymentPasswordFailedCount(memberId);
    }
}
