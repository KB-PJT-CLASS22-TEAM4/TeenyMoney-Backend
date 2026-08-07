package com.teenyfin.teenymoney.domain.payment.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.exception.CategoryPolicyErrorCode;
import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import com.teenyfin.teenymoney.domain.payment.dto.request.PaymentQrRequestDTO;
import com.teenyfin.teenymoney.domain.payment.dto.response.PaymentQrResponseDTO;
import com.teenyfin.teenymoney.domain.payment.exception.PaymentErrorCode;
import com.teenyfin.teenymoney.domain.payment.mapper.PaymentMapper;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final CategoryPolicyMapper categoryPolicyMapper;
    private final WalletMapper walletMapper;

    @Transactional(readOnly = true)
    public PaymentQrResponseDTO getPaymentInfo(Long memberId, String role, PaymentQrRequestDTO paymentQrRequestDTO) {

        // 만료 시간 검증
        if (paymentQrRequestDTO.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(PaymentErrorCode.QR_EXPIRED);
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

        return PaymentQrResponseDTO.builder()
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
}
