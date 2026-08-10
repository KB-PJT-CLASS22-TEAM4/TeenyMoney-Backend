package com.teenyfin.teenymoney.domain.payment.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.exception.CategoryPolicyErrorCode;
import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import com.teenyfin.teenymoney.domain.payment.dto.request.PaymentQrRequestDTO;
import com.teenyfin.teenymoney.domain.payment.dto.request.PaymentRequestDTO;
import com.teenyfin.teenymoney.domain.payment.dto.response.PaymentQrResponseDTO;
import com.teenyfin.teenymoney.domain.payment.dto.response.PaymentResponseDTO;
import com.teenyfin.teenymoney.domain.payment.exception.PaymentErrorCode;
import com.teenyfin.teenymoney.domain.payment.mapper.PaymentMapper;
import com.teenyfin.teenymoney.domain.payment.vo.PaymentInfoVO;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final CategoryPolicyMapper categoryPolicyMapper;
    private final WalletMapper walletMapper;
    private final PaymentInfoStore paymentInfoStore;

    @Transactional(readOnly = true)
    public PaymentQrResponseDTO getPaymentInfo(Long memberId, String role, PaymentQrRequestDTO paymentQrRequestDTO) {

        // 만료 시간 검증
        if (paymentQrRequestDTO.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(PaymentErrorCode.QR_EXPIRED);
        }

        Duration ttl = Duration.between(LocalDateTime.now(), paymentQrRequestDTO.getExpiredAt());

        // 동시 결제 시도 방지
        boolean locked = paymentInfoStore.tryLock(
                paymentQrRequestDTO.getMerchantCode(),
                paymentQrRequestDTO.getAmount(),
                ttl
        );

        if (!locked) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_IN_PROGRESS);
        }

        Long categoryId = categoryPolicyMapper.selectCategoryIdByMerchantCode(paymentQrRequestDTO.getMerchantCode());

        // DB에 존재하는 업종 코드인지 검증
        if (categoryId == null) {
            throw new BusinessException(PaymentErrorCode.INVALID_MERCHANT_CODE);
        }

        WalletVO walletVO = walletMapper.selectMemberWalletByMemberId(memberId);

        // 해당 자녀에게 지갑이 존재하는지 검증
        if (walletVO == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        // 잔액이 결제 금액 이상인지 검증
        if (walletVO.getBalance() < paymentQrRequestDTO.getAmount()) {
            throw new BusinessException(PaymentErrorCode.INSUFFICIENT_BALANCE);
        }

        // 업종 카테고리 정책 단계 확인
        CategoryPolicyVO categoryPolicyVO = categoryPolicyMapper.selectByMerchantCodeAndChildId(paymentQrRequestDTO.getMerchantCode(), memberId);

        // 해당 자녀에게 해당 업종 카테고리에 대해 정책이 설정되어있는지 검증
        if (categoryPolicyVO == null) {
            throw new BusinessException(CategoryPolicyErrorCode.CATEGORY_POLICY_NOT_FOUND);
        }

        Integer totalCount = null;
        Long totalAmount = null;

        // 주의 단계인 경우 최근 30일 간 결제한 횟수와 총 금액 계산
        if (categoryPolicyVO.getPolicy().equals("WATCH")) {
            totalCount = paymentMapper.countRecentTransactions(memberId, categoryId);
            totalAmount = paymentMapper.sumRecentTransactionAmount(memberId, categoryId);
        }

        // 결제 정보를 임시로 Redis에 저장, QR 만료 시각까지만 유효
        String paymentInfoId = UUID.randomUUID().toString();
        PaymentInfoVO paymentInfoVO = PaymentInfoVO.builder()
                .walletId(walletVO.getId())
                .merchantName(paymentQrRequestDTO.getMerchantName())
                .categoryPolicyId(categoryPolicyVO.getId())
                .amount(paymentQrRequestDTO.getAmount())
                .build();

        paymentInfoStore.save(paymentInfoId, paymentInfoVO, ttl);

        return PaymentQrResponseDTO.builder()
                .paymentInfoId(paymentInfoId)
                .merchantName(paymentQrRequestDTO.getMerchantName())
                .amount(paymentQrRequestDTO.getAmount())
                .balance(walletVO.getBalance())
                .categoryPolicy(CategoryPolicyResponseDTO.builder()
                        .id(categoryPolicyVO.getId())
                        .merchantCategoryName(categoryPolicyVO.getMerchantCategoryName())
                        .policy(categoryPolicyVO.getPolicy())
                        .build())
                .totalCount(totalCount)
                .totalAmount(totalAmount)
                .build();
    }

    @Transactional
    public PaymentResponseDTO progressPayment(Long memberId, String role, PaymentRequestDTO paymentRequestDTO) {

        PaymentInfoVO info = paymentInfoStore.find(paymentRequestDTO.getPaymentInfoId());

        if (info == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_INFO_EXPIRED); // QR 만료되었거나 잘못된 요청
        }

        // Redis에서 결제 정보 삭제 및 락 해제
        paymentInfoStore.delete(paymentRequestDTO.getPaymentInfoId());
        paymentInfoStore.unlock(info.getMerchantName(), info.getAmount());

        return PaymentResponseDTO.builder().build();
    }
}
