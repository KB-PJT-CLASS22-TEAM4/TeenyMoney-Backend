package com.teenyfin.teenymoney.domain.paymentPassword.service;

import com.teenyfin.teenymoney.domain.paymentPassword.mapper.PaymentPasswordMapper;
import com.teenyfin.teenymoney.domain.paymentPassword.vo.PaymentPasswordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentPasswordTransactionHelper {

    private final PaymentPasswordMapper paymentPasswordMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int incrementFailedCountAndGet(Long memberId) {
        paymentPasswordMapper.incrementPaymentPasswordFailedCount(memberId);
        PaymentPasswordVO updated = paymentPasswordMapper.selectByMemberId(memberId);

        return updated.getPaymentPasswordFailedCount();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updatePaymentLockedUntil(Long memberId, LocalDateTime lockedUntil) {
        paymentPasswordMapper.updatePaymentLockedUntil(memberId, lockedUntil);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetPaymentPasswordFailedCount(Long memberId) {
        paymentPasswordMapper.resetPaymentPasswordFailedCount(memberId);
    }
}
