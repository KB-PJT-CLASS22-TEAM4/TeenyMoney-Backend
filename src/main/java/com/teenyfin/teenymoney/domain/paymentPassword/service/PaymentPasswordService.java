package com.teenyfin.teenymoney.domain.paymentPassword.service;

import com.teenyfin.teenymoney.domain.paymentPassword.dto.request.PaymentPasswordRequestDTO;
import com.teenyfin.teenymoney.domain.paymentPassword.exception.PaymentPasswordErrorCode;
import com.teenyfin.teenymoney.domain.paymentPassword.mapper.PaymentPasswordMapper;
import com.teenyfin.teenymoney.domain.paymentPassword.vo.PaymentPasswordVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentPasswordService {

    private final PaymentPasswordMapper paymentPasswordMapper;
    private final PaymentPasswordTransactionHelper paymentPasswordTransactionHelper;
    private final PasswordEncoder passwordEncoder;

    // 결제 비밀번호 검증
    @Transactional
    public void checkPaymentPassword(Long memberId, String password) {
        PaymentPasswordVO paymentPasswordVO = paymentPasswordMapper.selectByMemberId(memberId);

        if (paymentPasswordVO.getPaymentPassword() == null) {
            throw new BusinessException(PaymentPasswordErrorCode.NOT_SET_PAYMENT_PASSWORD);
        }

        if (paymentPasswordVO.getPaymentLockedUntil() != null) {
            if (paymentPasswordVO.getPaymentLockedUntil().isAfter(LocalDateTime.now())) {
                throw new BusinessException(PaymentPasswordErrorCode.PAYMENT_LOCKED);
            }

            // 잠금이 풀린 후 최초로 시도한 결제면 결제 실패 횟수 초기화
            paymentPasswordTransactionHelper.updatePaymentLockedUntil(memberId, null);
            paymentPasswordTransactionHelper.resetPaymentPasswordFailedCount(memberId);
        }

        // 결제 비밀번호 검증
        if (!passwordEncoder.matches(password, paymentPasswordVO.getPaymentPassword())) {

            int failedCount = paymentPasswordTransactionHelper.incrementFailedCountAndGet(memberId); // 실패 횟수 1 증가

            // 증가 후 횟수가 5회면 잠금 시간 10분 후로 설정
            if (failedCount >= 5) {
                paymentPasswordTransactionHelper.updatePaymentLockedUntil(memberId, LocalDateTime.now().plusMinutes(10));
                throw new BusinessException(PaymentPasswordErrorCode.PAYMENT_JUST_LOCKED);
            }

            throw new BusinessException(PaymentPasswordErrorCode.INVALID_PAYMENT_PASSWORD);
        }

        // 비밀번호 일치 시 결제 실패 횟수 초기화
        paymentPasswordTransactionHelper.resetPaymentPasswordFailedCount(memberId);
    }

    // 결제 비밀번호 등록, 최초 1회만 실행
    @Transactional
    public void setPaymentPassword(Long memberId, PaymentPasswordRequestDTO paymentPasswordRequestDTO) {
        PaymentPasswordVO paymentPasswordVO = paymentPasswordMapper.selectByMemberId(memberId);

        if (paymentPasswordVO.getPaymentPassword() != null) {
            throw new BusinessException(PaymentPasswordErrorCode.ALREADY_SET_PAYMENT_PASSWORD);
        }

        paymentPasswordMapper.updatePaymentPassword(memberId, passwordEncoder.encode(paymentPasswordRequestDTO.getPassword()));
    }
}
