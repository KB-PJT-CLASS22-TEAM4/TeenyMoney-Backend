package com.teenyfin.teenymoney.domain.payment.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.exception.CategoryPolicyErrorCode;
import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicy;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.payment.dto.request.PaymentQrRequestDTO;
import com.teenyfin.teenymoney.domain.payment.dto.request.PaymentRequestDTO;
import com.teenyfin.teenymoney.domain.payment.dto.response.PaymentQrResponseDTO;
import com.teenyfin.teenymoney.domain.payment.dto.response.PaymentResponseDTO;
import com.teenyfin.teenymoney.domain.payment.exception.PaymentErrorCode;
import com.teenyfin.teenymoney.domain.payment.mapper.PaymentMapper;
import com.teenyfin.teenymoney.domain.payment.vo.OrderVO;
import com.teenyfin.teenymoney.domain.payment.vo.PaymentVO;
import com.teenyfin.teenymoney.domain.paymentPassword.service.PaymentPasswordService;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.WalletLedgerService;
import com.teenyfin.teenymoney.domain.wallet.vo.ReferenceType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletTransactionVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.sse.SseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final CategoryPolicyMapper categoryPolicyMapper;
    private final WalletMapper walletMapper;
    private final TeenyScoreMapper teenyScoreMapper;
    private final MemberMapper memberMapper;

    private final WalletLedgerService walletLedgerService;
    private final PaymentPasswordService paymentPasswordService;
    private final NotificationService notificationService;
    private final TeenyScorePolicyService teenyScorePolicyService;
    private final TeenyScoreChangeService teenyScoreChangeService;
    private final OrderStore orderStore;
    // 부모가 보는 자녀 지갑 갱신 신호. 부모 알림은 조건부라 이걸 대신하지 못한다.
    private final ApplicationEventPublisher eventPublisher;

    private static final int PAYMENT_WATCH_THRESHOLD_COUNT = 10;

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

        CategoryPolicy categoryPolicy = checkCategoryPolicy(memberId, orderVO.getCategoryId());
        String categoryName = categoryPolicyMapper.selectCategoryNameById(orderVO.getCategoryId());

        Integer totalCount = null;
        Long totalAmount = null;

        // 주의 단계인 경우 최근 30일 간 결제한 횟수와 총 금액 계산
        if (categoryPolicy == CategoryPolicy.WATCH) {
            totalCount = paymentMapper.countRecentTransactions(memberId, orderVO.getCategoryId());
            totalAmount = paymentMapper.sumRecentTransactionAmount(memberId, orderVO.getCategoryId());
        }

        return PaymentQrResponseDTO.builder()
                .orderId(paymentQrRequestDTO.getOrderId())
                .merchantName(orderVO.getMerchantName())
                .amount(orderVO.getAmount())
                .balance(walletVO.getBalance())
                .categoryPolicy(CategoryPolicyResponseDTO.builder()
                        .categoryName(categoryName)
                        .policy(categoryPolicy)
                        .build())
                .totalCount(totalCount)
                .totalAmount(totalAmount)
                .build();
    }

    // 결제 진행
    @Transactional
    public PaymentResponseDTO progressPayment(Long memberId, PaymentRequestDTO paymentRequestDTO) {

        // 동일한 사용자에 의한 중복 요청이면 기존 결과를 찾아서 그대로 반환
        PaymentVO existingPaymentVO = paymentMapper.selectByIdempotencyKey(paymentRequestDTO.getIdempotencyKey());

        if (existingPaymentVO != null) {

            CategoryPolicyVO categoryPolicyVO = categoryPolicyMapper.selectByCategoryIdAndChildId(existingPaymentVO.getCategoryId(), memberId);
            WalletVO walletVO = walletMapper.selectWalletForUpdate(existingPaymentVO.getWalletId());

            // 해당 결제 내역의 소유주가 현재 요청한 사용자와 다른 경우
            if (!Objects.equals(walletVO.getMemberId(), memberId)) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
            }

            WalletTransactionVO walletTransactionVO = walletMapper.selectByPaymentId(existingPaymentVO.getId());

            return PaymentResponseDTO.builder()
                    .merchantName(existingPaymentVO.getMerchantName())
                    .amount(existingPaymentVO.getAmount())
                    .balance(walletTransactionVO.getBalanceAfter())
                    .categoryPolicy(CategoryPolicyResponseDTO.builder()
                            .id(categoryPolicyVO.getId())
                            .categoryName(categoryPolicyVO.getCategoryName())
                            .policy(categoryPolicyVO.getPolicy())
                            .build())
                    .createdAt(existingPaymentVO.getCreatedAt())
                    .build();
        }

        // 새로운 결제 진행
        OrderVO orderVO = orderStore.find(paymentRequestDTO.getOrderId());

        // QR 결제 정보가 존재하는지 검증
        if (orderVO == null) {
            throw new BusinessException(PaymentErrorCode.INVALID_QR_CODE);
        }

        paymentPasswordService.checkPaymentPassword(memberId, paymentRequestDTO.getPassword());

        CategoryPolicy categoryPolicy = checkCategoryPolicy(memberId, orderVO.getCategoryId());
        String categoryName = categoryPolicyMapper.selectCategoryNameById(orderVO.getCategoryId());

        if (categoryPolicy == CategoryPolicy.BLOCK) {
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
                .merchantName(orderVO.getMerchantName())
                .appliedPolicy(categoryPolicy)
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
            // 다른 사용자로부터 이미 해당 주문이 처리된 경우 예외 반환
            throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_COMPLETED);
        }

        paymentVO = paymentMapper.selectById(paymentVO.getId());

        MemberVO memberVO = memberMapper.selectById(memberId);
        TeenyScoreGradeVO teenyScoreGradeVO = teenyScoreMapper.selectTeenyScoreGradeByChildId(memberId);

        // 주의 등급일 때
        if (categoryPolicy == CategoryPolicy.WATCH) {

            // 티니점수 감소 (월 결제 횟수가 PAYMENT_WATCH_THRESHOLD_COUNT 이상이면 추가 감소)
            int monthlyCount = paymentMapper.countRecentTransactions(memberId, orderVO.getCategoryId());
            teenyScoreChangeService.change(
                    teenyScorePolicyService.watchPayment(memberId, paymentVO.getId(), monthlyCount, PAYMENT_WATCH_THRESHOLD_COUNT));

            // 티니 등급이 3등급 이하면 부모에게 푸시 알림 발송
            if (teenyScoreGradeVO.getGradeId() <= 3) {

                String title = "자녀가 주의 업종에서 결제했어요";
                String content = memberVO.getName() + " · " + orderVO.getMerchantName();
                MemberParentVO memberParentVO = memberMapper.selectActiveParentByChildId(memberId);

                notificationService.createNotification(memberParentVO.getParentId(), title, content, NotificationReferenceType.PAYMENT, paymentVO.getId(), true);
            }
        }

        // 자녀에게 일반 알림 발송
        String title = "결제가 완료됐어요";
        String content = orderVO.getMerchantName() + " · " + orderVO.getAmount() + "원";

        notificationService.createNotification(memberId, title, content, NotificationReferenceType.PAYMENT, paymentVO.getId(), false);

        // 부모 화면에 걸린 자녀 잔액을 갱신시킨다.
        //
        // 위 알림으로는 대신할 수 없다. 부모 알림은 '주의 업종 + 3등급 이하'일 때만 나가지만,
        // 부모 홈은 결제 종류와 무관하게 자녀 지갑을 보고 있어서 매 결제마다 낡는다.
        // 알림 수신자("누구에게 알릴까")와 동기화 수신자("누구 화면이 낡았나")가 갈리는 자리다.
        publishParentWalletViewChangedBestEffort(memberId);

        // 최신 잔액 조회 (debit 이후 실제 반영된 값)
        walletVO = walletMapper.selectWalletForUpdate(walletVO.getId());

        return PaymentResponseDTO.builder()
                .merchantName(paymentVO.getMerchantName())
                .amount(paymentVO.getAmount())
                .balance(walletVO.getBalance())
                .categoryPolicy(CategoryPolicyResponseDTO.builder()
                        .categoryName(categoryName)
                        .policy(categoryPolicy)
                        .build())
                .createdAt(paymentVO.getCreatedAt())
                .build();
    }

    public CategoryPolicy checkCategoryPolicy(Long memberId, Long categoryId) {
        // 업종 카테고리 정책 단계 확인
        CategoryPolicyVO categoryPolicyVO = categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId);

        // 해당 자녀에게 해당 업종 카테고리에 대해 정책이 설정되어있는지 검증
        if (categoryPolicyVO == null) {
            throw new BusinessException(CategoryPolicyErrorCode.CATEGORY_POLICY_NOT_FOUND);
        }

        // 오늘만 허용이 적용되어 있는지 확인
        if (categoryPolicyMapper.existsApprovedTodayPermission(memberId, categoryId)) {
            return CategoryPolicy.ALLOW;
        }

        return categoryPolicyVO.getPolicy();
    }

    /**
     * 자녀 잔액이 줄었으니 부모 화면을 갱신시킨다.
     *
     * 연동된 부모가 없으면 아무 일도 하지 않는다. 발행 실패로 결제가 되돌아가면 안 되므로
     * best-effort다 - 알림 전송과 같은 원칙이다.
     */
    private void publishParentWalletViewChangedBestEffort(Long childId) {
        try {
            MemberParentVO parent = memberMapper.selectActiveParentByChildId(childId);
            if (parent == null || parent.getParentId() == null) {
                return;
            }
            eventPublisher.publishEvent(
                    new SseEvent(parent.getParentId(), NotificationReferenceType.PAYMENT));
        } catch (RuntimeException e) {
            log.error("부모 지갑 화면 갱신 신호 발행 중 오류 - childId={}", childId, e);
        }
    }
}
