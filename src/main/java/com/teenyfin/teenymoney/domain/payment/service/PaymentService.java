package com.teenyfin.teenymoney.domain.payment.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.exception.CategoryPolicyErrorCode;
import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import com.teenyfin.teenymoney.domain.payment.dto.request.PaymentPasswordRequestDTO;
import com.teenyfin.teenymoney.domain.payment.dto.request.PaymentQrRequestDTO;
import com.teenyfin.teenymoney.domain.payment.dto.request.PaymentRequestDTO;
import com.teenyfin.teenymoney.domain.payment.dto.response.PaymentQrResponseDTO;
import com.teenyfin.teenymoney.domain.payment.dto.response.PaymentResponseDTO;
import com.teenyfin.teenymoney.domain.payment.exception.PaymentErrorCode;
import com.teenyfin.teenymoney.domain.payment.mapper.MemberPaymentMapper;
import com.teenyfin.teenymoney.domain.payment.mapper.PaymentMapper;
import com.teenyfin.teenymoney.domain.payment.vo.MemberPaymentVO;
import com.teenyfin.teenymoney.domain.payment.vo.OrderVO;
import com.teenyfin.teenymoney.domain.payment.vo.PaymentVO;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.WalletLedgerService;
import com.teenyfin.teenymoney.domain.wallet.vo.ReferenceType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final PaymentMapper paymentMapper;
    private final CategoryPolicyMapper categoryPolicyMapper;
    private final MemberPaymentMapper memberPaymentMapper;
    private final WalletMapper walletMapper;

    private final WalletLedgerService walletLedgerService;
    private final MemberPaymentService memberPaymentService;

    private final OrderStore orderStore;
    private final PasswordEncoder passwordEncoder;

    // QR 코드로 주문 정보를 Redis에 저장 후 반환
    @Transactional(readOnly = true)
    public PaymentQrResponseDTO getPaymentInfo(Long memberId, PaymentQrRequestDTO paymentQrRequestDTO) {

        // 만료 시간 검증
        if (paymentQrRequestDTO.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(PaymentErrorCode.EXPIRED_QR_CODE);
        }

        // 이미 결제 완료된 주문인지 먼저 확인
        if (paymentMapper.existsByOrderId(paymentQrRequestDTO.getOrderId())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_COMPLETED);
        }

        // 이미 Redis에 저장된 주문 정보가 있으면 재사용
        OrderVO orderVO = orderStore.find(paymentQrRequestDTO.getOrderId());

        if (orderVO == null) {

            Long categoryId = categoryPolicyMapper.selectCategoryIdByMerchantCode(paymentQrRequestDTO.getMerchantCode());

            // DB에 존재하는 업종 코드인지 검증
            if (categoryId == null) {
                throw new BusinessException(PaymentErrorCode.INVALID_MERCHANT_CODE);
            }

            // 주문 정보를 임시로 Redis에 저장, QR 만료 시각까지만 유효
            orderVO = OrderVO.builder()
                    .merchantName(paymentQrRequestDTO.getMerchantName())
                    .categoryId(categoryId)
                    .amount(paymentQrRequestDTO.getAmount())
                    .build();

            Duration ttl = Duration.between(LocalDateTime.now(), paymentQrRequestDTO.getExpiredAt());
            orderStore.save(paymentQrRequestDTO.getOrderId(), orderVO, ttl);
        }

        WalletVO walletVO = walletMapper.selectMemberWalletByMemberId(memberId);

        // 해당 자녀에게 지갑이 존재하는지 검증
        if (walletVO == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        // 잔액이 결제 금액 이상인지 검증 (Redis에 저장된 검증값 기준)
        if (walletVO.getBalance() < orderVO.getAmount()) {
            throw new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE);
        }

        // 업종 카테고리 정책 단계 확인
        CategoryPolicyVO categoryPolicyVO = categoryPolicyMapper.selectByCategoryIdAndChildId(orderVO.getCategoryId(), memberId);

        // 해당 자녀에게 해당 업종 카테고리에 대해 정책이 설정되어있는지 검증
        if (categoryPolicyVO == null) {
            throw new BusinessException(CategoryPolicyErrorCode.CATEGORY_POLICY_NOT_FOUND);
        }

        Integer totalCount = null;
        Long totalAmount = null;

        // 주의 단계인 경우 최근 30일 간 결제한 횟수와 총 금액 계산
        if (categoryPolicyVO.getPolicy().equals("WATCH")) {
            totalCount = paymentMapper.countRecentTransactions(memberId, orderVO.getCategoryId());
            totalAmount = paymentMapper.sumRecentTransactionAmount(memberId, orderVO.getCategoryId());
        }

        return PaymentQrResponseDTO.builder()
                .orderId(paymentQrRequestDTO.getOrderId())
                .merchantName(orderVO.getMerchantName())
                .amount(orderVO.getAmount())
                .balance(walletVO.getBalance())
                .categoryPolicy(CategoryPolicyResponseDTO.builder()
                        .id(categoryPolicyVO.getId())
                        .categoryName(categoryPolicyVO.getCategoryName())
                        .policy(categoryPolicyVO.getPolicy())
                        .build())
                .totalCount(totalCount)
                .totalAmount(totalAmount)
                .build();
    }

    // 결제 진행
    @Transactional
    public PaymentResponseDTO progressPayment(Long memberId, PaymentRequestDTO paymentRequestDTO) {

        OrderVO orderVO = orderStore.find(paymentRequestDTO.getOrderId());

        // QR 결제 정보가 존재하는지 검증
        if (orderVO == null) {
            throw new BusinessException(PaymentErrorCode.INVALID_QR_CODE);
        }

        MemberPaymentVO memberPaymentVO = memberPaymentMapper.selectByMemberId(memberId);

        if (memberPaymentVO.getPaymentPassword() == null) {
            throw new BusinessException(PaymentErrorCode.NOT_SET_PAYMENT_PASSWORD);
        }

        // 결제 잠금 상태면 막음
        if (memberPaymentVO.getPaymentLockedUntil() != null
                && memberPaymentVO.getPaymentLockedUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_LOCKED);
        }

        // 결제 비밀번호 검증
        if (!passwordEncoder.matches(paymentRequestDTO.getPassword(), memberPaymentVO.getPaymentPassword())) {

            int failedCount = memberPaymentService.incrementFailedCountAndGet(memberId); // 실패 횟수 1 증가

            // 증가 후 횟수가 5회면 잠금 시간 10분 후로 설정
            if (failedCount >= 5) {
                memberPaymentService.lockPayment(memberId, LocalDateTime.now().plusMinutes(10));
                throw new BusinessException(PaymentErrorCode.PAYMENT_JUST_LOCKED);
            }

            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_PASSWORD);
        }

        // 성공하면 실패 횟수 초기화
        memberPaymentService.resetFailedCount(memberId);

        CategoryPolicyVO categoryPolicyVO = categoryPolicyMapper.selectByCategoryIdAndChildId(orderVO.getCategoryId(), memberId);

        // 해당 카테고리 정책이 존재하는지 검증
        if (categoryPolicyVO == null) {
            throw new BusinessException(CategoryPolicyErrorCode.CATEGORY_POLICY_NOT_FOUND);
        }

        if (categoryPolicyVO.getPolicy().equals("BLOCK")) {
            throw new BusinessException(PaymentErrorCode.BLOCKED_CATEGORY); // 차단된 카테고리
        }

        WalletVO walletVO = walletMapper.selectMemberWalletByMemberId(memberId);

        // 해당 자녀에게 지갑이 존재하는지 검증
        if (walletVO == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }

        PaymentVO paymentVO = PaymentVO.builder()
                .walletId(walletVO.getId())
                .categoryId(orderVO.getCategoryId())
                .orderId(paymentRequestDTO.getOrderId())
                .idempotencyKey(paymentRequestDTO.getIdempotencyKey())
                .appliedPolicy(categoryPolicyVO.getPolicy())
                .amount(orderVO.getAmount())
                .status("SUCCESS")
                .build();

        try {
            // 결제 내역 삽입
            paymentMapper.insert(paymentVO);

            // 지갑 잔액 변경 및 원장 기입
            walletLedgerService.debit(
                    paymentVO.getWalletId(),
                    orderVO.getAmount(),
                    ReferenceType.PAYMENT,
                    paymentVO.getId(),
                    orderVO.getMerchantName());

        } catch (DuplicateKeyException e) {
            // 동일한 사용자에 의한 중복 요청이면 기존 결과를 찾아서 그대로 반환
            paymentVO = paymentMapper.selectByIdempotencyKey(paymentRequestDTO.getIdempotencyKey());

            // 다른 사용자로부터 이미 해당 주문이 처리된 경우 예외 반환
            if (paymentVO == null) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_COMPLETED);
            }
        }

        paymentVO = paymentMapper.selectById(paymentVO.getId());

        // 최신 잔액 조회 (debit 이후 실제 반영된 값)
        walletVO = walletMapper.selectWalletForUpdate(walletVO.getId());

        return PaymentResponseDTO.builder()
                .merchantName(orderVO.getMerchantName())
                .amount(paymentVO.getAmount())
                .balance(walletVO.getBalance())
                .categoryPolicy(CategoryPolicyResponseDTO.builder()
                        .id(categoryPolicyVO.getId())
                        .categoryName(categoryPolicyVO.getCategoryName())
                        .policy(categoryPolicyVO.getPolicy())
                        .build())
                .createdAt(paymentVO.getCreatedAt())
                .build();
    }

    // 결제 비밀번호 등록, 최초 1회만 실행
    @Transactional
    public void setPaymentPassword(Long memberId, PaymentPasswordRequestDTO paymentPasswordRequestDTO) {
        MemberPaymentVO memberPaymentVO = memberPaymentMapper.selectByMemberId(memberId);

        if (memberPaymentVO.getPaymentPassword() != null) {
            throw new BusinessException(PaymentErrorCode.ALREADY_SET_PAYMENT_PASSWORD);
        }

        memberPaymentMapper.updatePaymentPassword(memberId, passwordEncoder.encode(paymentPasswordRequestDTO.getPassword()));
    }
}
