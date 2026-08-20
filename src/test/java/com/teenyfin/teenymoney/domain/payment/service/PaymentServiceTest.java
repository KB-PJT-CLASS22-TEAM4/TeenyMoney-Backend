package com.teenyfin.teenymoney.domain.payment.service;

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
import com.teenyfin.teenymoney.domain.payment.mapper.PaymentMapper;
import com.teenyfin.teenymoney.domain.payment.vo.OrderVO;
import com.teenyfin.teenymoney.domain.payment.vo.PaymentVO;
import com.teenyfin.teenymoney.domain.paymentPassword.service.PaymentPasswordService;
import com.teenyfin.teenymoney.domain.teenyscore.dto.request.TeenyScoreChangeRequestDTO;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreEventCode;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeVO;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.WalletLedgerService;
import com.teenyfin.teenymoney.domain.wallet.vo.ReferenceType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletTransactionVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import com.teenyfin.teenymoney.global.sse.SseEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PaymentServiceTest {

    private final PaymentMapper paymentMapper = Mockito.mock(PaymentMapper.class);
    private final CategoryPolicyMapper categoryPolicyMapper = Mockito.mock(CategoryPolicyMapper.class);
    private final WalletMapper walletMapper = Mockito.mock(WalletMapper.class);
    private final TeenyScoreMapper teenyScoreMapper = Mockito.mock(TeenyScoreMapper.class);
    private final MemberMapper memberMapper = Mockito.mock(MemberMapper.class);
    private final WalletLedgerService walletLedgerService = Mockito.mock(WalletLedgerService.class);
    private final PaymentPasswordService paymentPasswordService = Mockito.mock(PaymentPasswordService.class);
    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    // 실제 감점 정책을 검증하기 위해 목이 아닌 실제 구현체를 사용한다 (다른 도메인 테스트와 동일한 방식)
    private final TeenyScorePolicyService teenyScorePolicyService = new TeenyScorePolicyService();
    private final TeenyScoreChangeService teenyScoreChangeService = Mockito.mock(TeenyScoreChangeService.class);
    private final OrderStore orderStore = Mockito.mock(OrderStore.class);
    private final ApplicationEventPublisher eventPublisher =
            Mockito.mock(ApplicationEventPublisher.class);
    private final PaymentService paymentService = new PaymentService(
            paymentMapper, categoryPolicyMapper, walletMapper, teenyScoreMapper, memberMapper,
            walletLedgerService, paymentPasswordService, notificationService, teenyScorePolicyService,
            teenyScoreChangeService, orderStore, eventPublisher);

    private WalletVO createWalletVO(Long id, Long memberId, Long balance) {
        WalletVO vo = new WalletVO();
        vo.setId(id);
        vo.setMemberId(memberId);
        vo.setBalance(balance);
        return vo;
    }

    private CategoryPolicyVO createCategoryPolicyVO(Long id, String categoryName, CategoryPolicy policy) {
        return CategoryPolicyVO.builder()
                .id(id)
                .categoryName(categoryName)
                .policy(policy)
                .build();
    }

    // ---------- getPaymentInfo() ----------

    @Test
    void 만료된_QR이면_예외를_던지고_아무것도_조회하지_않는다() {
        Long memberId = 1L;
        PaymentQrRequestDTO requestDTO = PaymentQrRequestDTO.builder()
                .orderId("ORDER-001")
                .merchantCode("552101")
                .merchantName("CU 강남역점")
                .amount(3000L)
                .expiredAt(LocalDateTime.now().minusMinutes(1))
                .build();

        assertThatThrownBy(() -> paymentService.getPaymentInfo(memberId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(orderStore, never()).find(any());
        verify(walletMapper, never()).selectMemberWalletByMemberId(any());
    }

    @Test
    void 이미_결제_완료된_주문이면_예외를_던진다() {
        Long memberId = 1L;
        PaymentQrRequestDTO requestDTO = PaymentQrRequestDTO.builder()
                .orderId("ORDER-001")
                .merchantCode("552101")
                .merchantName("CU 강남역점")
                .amount(3000L)
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build();

        given(paymentMapper.existsByOrderId("ORDER-001")).willReturn(true);

        assertThatThrownBy(() -> paymentService.getPaymentInfo(memberId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(orderStore, never()).find(any());
    }

    @Test
    void 신규_주문이면_업종코드로_카테고리를_조회하고_Redis에_저장한다() {
        Long memberId = 1L;
        Long categoryId = 5L;
        PaymentQrRequestDTO requestDTO = PaymentQrRequestDTO.builder()
                .orderId("ORDER-001")
                .merchantCode("552101")
                .merchantName("CU 강남역점")
                .amount(3000L)
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build();

        given(paymentMapper.existsByOrderId("ORDER-001")).willReturn(false);
        given(orderStore.find("ORDER-001")).willReturn(null);
        given(categoryPolicyMapper.selectCategoryIdByMerchantCode("552101")).willReturn(categoryId);

        WalletVO walletVO = createWalletVO(10L, memberId, 50000L);
        given(walletMapper.selectMemberWalletByMemberId(memberId)).willReturn(walletVO);

        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId))
                .willReturn(createCategoryPolicyVO(100L, "편의점", CategoryPolicy.ALLOW));

        PaymentQrResponseDTO result = paymentService.getPaymentInfo(memberId, requestDTO);

        ArgumentCaptor<OrderVO> captor = ArgumentCaptor.forClass(OrderVO.class);
        verify(orderStore).save(eq("ORDER-001"), captor.capture(), any(Duration.class));

        OrderVO savedOrderVO = captor.getValue();
        assertThat(savedOrderVO.getMerchantName()).isEqualTo("CU 강남역점");
        assertThat(savedOrderVO.getCategoryId()).isEqualTo(categoryId);
        assertThat(savedOrderVO.getAmount()).isEqualTo(3000L);

        assertThat(result.getOrderId()).isEqualTo("ORDER-001");
        assertThat(result.getBalance()).isEqualTo(50000L);
        assertThat(result.getCategoryPolicy().getPolicy()).isEqualTo(CategoryPolicy.ALLOW);
    }

    @Test
    void 존재하지_않는_업종코드면_예외를_던지고_저장하지_않는다() {
        Long memberId = 1L;
        PaymentQrRequestDTO requestDTO = PaymentQrRequestDTO.builder()
                .orderId("ORDER-001")
                .merchantCode("999999")
                .merchantName("알수없는가게")
                .amount(3000L)
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build();

        given(paymentMapper.existsByOrderId("ORDER-001")).willReturn(false);
        given(orderStore.find("ORDER-001")).willReturn(null);
        given(categoryPolicyMapper.selectCategoryIdByMerchantCode("999999")).willReturn(null);

        assertThatThrownBy(() -> paymentService.getPaymentInfo(memberId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(orderStore, never()).save(any(), any(), any());
    }

    @Test
    void 이미_Redis에_저장된_주문이면_재사용하고_카테고리_재조회하지_않는다() {
        Long memberId = 1L;
        Long categoryId = 5L;
        PaymentQrRequestDTO requestDTO = PaymentQrRequestDTO.builder()
                .orderId("ORDER-001")
                .merchantCode("552101")
                .merchantName("CU 강남역점")
                .amount(3000L)
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build();

        given(paymentMapper.existsByOrderId("ORDER-001")).willReturn(false);

        OrderVO existingOrderVO = OrderVO.builder()
                .merchantName("CU 강남역점")
                .categoryId(categoryId)
                .amount(3000L)
                .build();
        given(orderStore.find("ORDER-001")).willReturn(existingOrderVO);

        WalletVO walletVO = createWalletVO(10L, memberId, 50000L);
        given(walletMapper.selectMemberWalletByMemberId(memberId)).willReturn(walletVO);

        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId))
                .willReturn(createCategoryPolicyVO(100L, "편의점", CategoryPolicy.ALLOW));

        PaymentQrResponseDTO result = paymentService.getPaymentInfo(memberId, requestDTO);

        verify(categoryPolicyMapper, never()).selectCategoryIdByMerchantCode(any());
        verify(orderStore, never()).save(any(), any(), any());
        assertThat(result.getOrderId()).isEqualTo("ORDER-001");
    }

    @Test
    void 지갑이_없으면_예외를_던진다() {
        Long memberId = 1L;
        Long categoryId = 5L;
        PaymentQrRequestDTO requestDTO = PaymentQrRequestDTO.builder()
                .orderId("ORDER-001")
                .merchantCode("552101")
                .merchantName("CU 강남역점")
                .amount(3000L)
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build();

        given(paymentMapper.existsByOrderId("ORDER-001")).willReturn(false);
        given(orderStore.find("ORDER-001")).willReturn(null);
        given(categoryPolicyMapper.selectCategoryIdByMerchantCode("552101")).willReturn(categoryId);
        given(walletMapper.selectMemberWalletByMemberId(memberId)).willReturn(null);

        assertThatThrownBy(() -> paymentService.getPaymentInfo(memberId, requestDTO))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 잔액이_부족하면_예외를_던진다() {
        Long memberId = 1L;
        Long categoryId = 5L;
        PaymentQrRequestDTO requestDTO = PaymentQrRequestDTO.builder()
                .orderId("ORDER-001")
                .merchantCode("552101")
                .merchantName("CU 강남역점")
                .amount(3000L)
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build();

        given(paymentMapper.existsByOrderId("ORDER-001")).willReturn(false);
        given(orderStore.find("ORDER-001")).willReturn(null);
        given(categoryPolicyMapper.selectCategoryIdByMerchantCode("552101")).willReturn(categoryId);

        WalletVO walletVO = createWalletVO(10L, memberId, 1000L);
        given(walletMapper.selectMemberWalletByMemberId(memberId)).willReturn(walletVO);

        assertThatThrownBy(() -> paymentService.getPaymentInfo(memberId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(categoryPolicyMapper, never()).selectByCategoryIdAndChildId(any(), any());
    }

    @Test
    void 카테고리_정책이_없으면_예외를_던진다() {
        Long memberId = 1L;
        Long categoryId = 5L;
        PaymentQrRequestDTO requestDTO = PaymentQrRequestDTO.builder()
                .orderId("ORDER-001")
                .merchantCode("552101")
                .merchantName("CU 강남역점")
                .amount(3000L)
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build();

        given(paymentMapper.existsByOrderId("ORDER-001")).willReturn(false);
        given(orderStore.find("ORDER-001")).willReturn(null);
        given(categoryPolicyMapper.selectCategoryIdByMerchantCode("552101")).willReturn(categoryId);

        WalletVO walletVO = createWalletVO(10L, memberId, 50000L);
        given(walletMapper.selectMemberWalletByMemberId(memberId)).willReturn(walletVO);
        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId)).willReturn(null);

        assertThatThrownBy(() -> paymentService.getPaymentInfo(memberId, requestDTO))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void WATCH_정책이면_최근_소비_이력을_계산해서_응답에_포함한다() {
        Long memberId = 1L;
        Long categoryId = 5L;
        PaymentQrRequestDTO requestDTO = PaymentQrRequestDTO.builder()
                .orderId("ORDER-001")
                .merchantCode("552101")
                .merchantName("CU 강남역점")
                .amount(3000L)
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build();

        given(paymentMapper.existsByOrderId("ORDER-001")).willReturn(false);
        given(orderStore.find("ORDER-001")).willReturn(null);
        given(categoryPolicyMapper.selectCategoryIdByMerchantCode("552101")).willReturn(categoryId);

        WalletVO walletVO = createWalletVO(10L, memberId, 50000L);
        given(walletMapper.selectMemberWalletByMemberId(memberId)).willReturn(walletVO);

        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId))
                .willReturn(createCategoryPolicyVO(100L, "PC방", CategoryPolicy.WATCH));

        given(paymentMapper.countRecentTransactions(memberId, categoryId)).willReturn(5);
        given(paymentMapper.sumRecentTransactionAmount(memberId, categoryId)).willReturn(45000L);

        PaymentQrResponseDTO result = paymentService.getPaymentInfo(memberId, requestDTO);

        assertThat(result.getTotalCount()).isEqualTo(5);
        assertThat(result.getTotalAmount()).isEqualTo(45000L);
    }

    @Test
    void 오늘만_허용이_승인되어_있으면_저장된_정책과_무관하게_ALLOW로_취급하고_소비이력은_계산하지_않는다() {
        Long memberId = 1L;
        Long categoryId = 5L;
        PaymentQrRequestDTO requestDTO = PaymentQrRequestDTO.builder()
                .orderId("ORDER-001")
                .merchantCode("552101")
                .merchantName("CU 강남역점")
                .amount(3000L)
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build();

        given(paymentMapper.existsByOrderId("ORDER-001")).willReturn(false);
        given(orderStore.find("ORDER-001")).willReturn(null);
        given(categoryPolicyMapper.selectCategoryIdByMerchantCode("552101")).willReturn(categoryId);

        WalletVO walletVO = createWalletVO(10L, memberId, 50000L);
        given(walletMapper.selectMemberWalletByMemberId(memberId)).willReturn(walletVO);

        // 실제 저장된 정책은 WATCH지만, 오늘 승인된 "오늘만 허용" 요청이 있다
        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId))
                .willReturn(createCategoryPolicyVO(100L, "PC방", CategoryPolicy.WATCH));
        given(categoryPolicyMapper.existsApprovedTodayPermission(memberId, categoryId)).willReturn(true);

        PaymentQrResponseDTO result = paymentService.getPaymentInfo(memberId, requestDTO);

        assertThat(result.getCategoryPolicy().getPolicy()).isEqualTo(CategoryPolicy.ALLOW);
        assertThat(result.getTotalCount()).isNull();
        assertThat(result.getTotalAmount()).isNull();
        verify(paymentMapper, never()).countRecentTransactions(any(), any());
    }

    // ---------- progressPayment() ----------

    @Test
    void 멱등키가_재사용되면_기존_결제_내역을_그대로_반환하고_새로_결제하지_않는다() {
        Long memberId = 2L;
        Long walletId = 10L;
        Long paymentId = 999L;
        Long categoryId = 5L;

        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .orderId("ORDER-001")
                .idempotencyKey("IDEMP-001")
                .password("123456")
                .build();

        PaymentVO existingPaymentVO = PaymentVO.builder()
                .id(paymentId)
                .walletId(walletId)
                .categoryId(categoryId)
                .merchantName("CU 강남역점")
                .amount(3000L)
                .createdAt(LocalDateTime.now())
                .build();
        given(paymentMapper.selectByIdempotencyKey("IDEMP-001")).willReturn(existingPaymentVO);

        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId))
                .willReturn(createCategoryPolicyVO(100L, "편의점", CategoryPolicy.ALLOW));

        WalletVO walletVO = createWalletVO(walletId, memberId, 47000L);
        given(walletMapper.selectWalletForUpdate(walletId)).willReturn(walletVO);

        WalletTransactionVO walletTransactionVO = new WalletTransactionVO();
        walletTransactionVO.setBalanceAfter(47000L);
        given(walletMapper.selectByPaymentId(paymentId)).willReturn(walletTransactionVO);

        PaymentResponseDTO result = paymentService.progressPayment(memberId, requestDTO);

        assertThat(result.getMerchantName()).isEqualTo("CU 강남역점");
        assertThat(result.getAmount()).isEqualTo(3000L);
        assertThat(result.getBalance()).isEqualTo(47000L);

        verify(paymentMapper, never()).insert(any());
        verify(walletLedgerService, never()).debit(any(), any(), any(), any(), any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 멱등키로_찾은_결제의_소유주가_다르면_예외를_던진다() {
        Long memberId = 2L;
        Long walletId = 10L;

        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .orderId("ORDER-001")
                .idempotencyKey("IDEMP-001")
                .password("123456")
                .build();

        PaymentVO existingPaymentVO = PaymentVO.builder()
                .id(999L)
                .walletId(walletId)
                .categoryId(5L)
                .merchantName("CU 강남역점")
                .amount(3000L)
                .build();
        given(paymentMapper.selectByIdempotencyKey("IDEMP-001")).willReturn(existingPaymentVO);

        // 지갑 소유주가 요청자(memberId=2)와 다른 회원(999)
        WalletVO walletVO = createWalletVO(walletId, 999L, 47000L);
        given(walletMapper.selectWalletForUpdate(walletId)).willReturn(walletVO);

        assertThatThrownBy(() -> paymentService.progressPayment(memberId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(walletMapper, never()).selectByPaymentId(any());
    }

    @Test
    void QR_정보가_없으면_예외를_던지고_결제_비밀번호도_확인하지_않는다() {
        Long memberId = 2L;

        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .orderId("ORDER-001")
                .idempotencyKey("IDEMP-001")
                .password("123456")
                .build();

        given(paymentMapper.selectByIdempotencyKey("IDEMP-001")).willReturn(null);
        given(orderStore.find("ORDER-001")).willReturn(null);

        assertThatThrownBy(() -> paymentService.progressPayment(memberId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(paymentPasswordService, never()).checkPaymentPassword(any(), any());
    }

    @Test
    void 결제_비밀번호가_틀리면_예외를_던지고_결제하지_않는다() {
        Long memberId = 2L;

        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .orderId("ORDER-001")
                .idempotencyKey("IDEMP-001")
                .password("000000")
                .build();

        given(paymentMapper.selectByIdempotencyKey("IDEMP-001")).willReturn(null);
        given(orderStore.find("ORDER-001")).willReturn(
                OrderVO.builder().merchantName("CU 강남역점").categoryId(5L).amount(3000L).build());

        doThrow(new BusinessException(com.teenyfin.teenymoney.domain.paymentPassword.exception.PaymentPasswordErrorCode.INVALID_PAYMENT_PASSWORD))
                .when(paymentPasswordService).checkPaymentPassword(memberId, "000000");

        assertThatThrownBy(() -> paymentService.progressPayment(memberId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(categoryPolicyMapper, never()).selectByCategoryIdAndChildId(any(), any());
        verify(paymentMapper, never()).insert(any());
    }

    @Test
    void 차단된_카테고리면_예외를_던지고_결제하지_않는다() {
        Long memberId = 2L;
        Long categoryId = 5L;

        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .orderId("ORDER-001")
                .idempotencyKey("IDEMP-001")
                .password("123456")
                .build();

        given(paymentMapper.selectByIdempotencyKey("IDEMP-001")).willReturn(null);
        given(orderStore.find("ORDER-001")).willReturn(
                OrderVO.builder().merchantName("성인오락실").categoryId(categoryId).amount(3000L).build());

        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId))
                .willReturn(createCategoryPolicyVO(100L, "유해업종", CategoryPolicy.BLOCK));

        assertThatThrownBy(() -> paymentService.progressPayment(memberId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(walletMapper, never()).selectMemberWalletByMemberId(any());
        verify(paymentMapper, never()).insert(any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 오늘만_허용이_승인되어_있으면_차단된_카테고리도_결제가_진행된다() {
        Long memberId = 2L;
        Long categoryId = 5L;
        Long walletId = 10L;

        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .orderId("ORDER-001")
                .idempotencyKey("IDEMP-001")
                .password("123456")
                .build();

        given(paymentMapper.selectByIdempotencyKey("IDEMP-001")).willReturn(null);
        given(orderStore.find("ORDER-001")).willReturn(
                OrderVO.builder().merchantName("PC방").categoryId(categoryId).amount(3000L).build());

        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId))
                .willReturn(createCategoryPolicyVO(100L, "PC방", CategoryPolicy.BLOCK));
        given(categoryPolicyMapper.existsApprovedTodayPermission(memberId, categoryId)).willReturn(true);

        WalletVO walletVO = createWalletVO(walletId, memberId, 50000L);
        given(walletMapper.selectMemberWalletByMemberId(memberId)).willReturn(walletVO);

        stubInsertAssignsId(1000L);

        PaymentVO stored = PaymentVO.builder()
                .id(1000L).walletId(walletId).categoryId(categoryId)
                .merchantName("PC방").amount(3000L).createdAt(LocalDateTime.now())
                .build();
        given(paymentMapper.selectById(1000L)).willReturn(stored);

        MemberVO childVO = new MemberVO();
        childVO.setName("김첫째");
        given(memberMapper.selectById(memberId)).willReturn(childVO);

        given(walletMapper.selectWalletForUpdate(walletId)).willReturn(createWalletVO(walletId, memberId, 47000L));

        PaymentResponseDTO result = paymentService.progressPayment(memberId, requestDTO);

        assertThat(result.getCategoryPolicy().getPolicy()).isEqualTo(CategoryPolicy.ALLOW);
        verify(paymentMapper).insert(any());
        verify(walletLedgerService).debit(walletId, 3000L, ReferenceType.PAYMENT, 1000L, "PC방");
    }

    @Test
    void 결제_시_지갑이_없으면_예외를_던진다() {
        Long memberId = 2L;
        Long categoryId = 5L;

        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .orderId("ORDER-001")
                .idempotencyKey("IDEMP-001")
                .password("123456")
                .build();

        given(paymentMapper.selectByIdempotencyKey("IDEMP-001")).willReturn(null);
        given(orderStore.find("ORDER-001")).willReturn(
                OrderVO.builder().merchantName("CU 강남역점").categoryId(categoryId).amount(3000L).build());

        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId))
                .willReturn(createCategoryPolicyVO(100L, "편의점", CategoryPolicy.ALLOW));
        given(walletMapper.selectMemberWalletByMemberId(memberId)).willReturn(null);

        assertThatThrownBy(() -> paymentService.progressPayment(memberId, requestDTO))
                .isInstanceOf(BusinessException.class);

        verify(paymentMapper, never()).insert(any());
    }

    // insertPayment 호출 시 useGeneratedKeys를 흉내 내서 id를 채워준다 (PaymentVO엔 setter가 없어 리플렉션 사용)
    private void stubInsertAssignsId(long generatedId) {
        Mockito.doAnswer(invocation -> {
            PaymentVO vo = invocation.getArgument(0);
            ReflectionTestUtils.setField(vo, "id", generatedId);
            return 1;
        }).when(paymentMapper).insert(any());
    }

    @Test
    void 정상_결제되면_지갑에서_차감되고_자녀에게_알림이_발송된다() {
        Long memberId = 2L;
        Long categoryId = 5L;
        Long walletId = 10L;

        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .orderId("ORDER-001")
                .idempotencyKey("IDEMP-001")
                .password("123456")
                .build();

        given(paymentMapper.selectByIdempotencyKey("IDEMP-001")).willReturn(null);
        given(orderStore.find("ORDER-001")).willReturn(
                OrderVO.builder().merchantName("CU 강남역점").categoryId(categoryId).amount(3000L).build());

        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId))
                .willReturn(createCategoryPolicyVO(100L, "편의점", CategoryPolicy.ALLOW));

        WalletVO walletBeforeDebit = createWalletVO(walletId, memberId, 50000L);
        given(walletMapper.selectMemberWalletByMemberId(memberId)).willReturn(walletBeforeDebit);

        stubInsertAssignsId(1000L);

        PaymentVO stored = PaymentVO.builder()
                .id(1000L).walletId(walletId).categoryId(categoryId)
                .merchantName("CU 강남역점").amount(3000L).createdAt(LocalDateTime.now())
                .build();
        given(paymentMapper.selectById(1000L)).willReturn(stored);

        MemberVO childVO = new MemberVO();
        childVO.setName("김첫째");
        given(memberMapper.selectById(memberId)).willReturn(childVO);

        given(walletMapper.selectWalletForUpdate(walletId)).willReturn(createWalletVO(walletId, memberId, 47000L));

        PaymentResponseDTO result = paymentService.progressPayment(memberId, requestDTO);

        // then: 지갑에서 실제로 차감 로직이 호출됐는지
        verify(walletLedgerService).debit(walletId, 3000L, ReferenceType.PAYMENT, 1000L, "CU 강남역점");

        // then: 자녀에게만 알림이 가고(WATCH 아님) 조용히(isPushed=false) 남는다
        verify(notificationService).createNotification(
                eq(memberId), eq("결제가 완료됐어요"), eq("CU 강남역점 · 3000원"),
                eq(NotificationReferenceType.PAYMENT), eq(1000L), eq(false));
        verify(notificationService, Mockito.times(1))
                .createNotification(any(), any(), any(), any(), any(), any());

        assertThat(result.getMerchantName()).isEqualTo("CU 강남역점");
        assertThat(result.getAmount()).isEqualTo(3000L);
        assertThat(result.getBalance()).isEqualTo(47000L);
        assertThat(result.getCategoryPolicy().getPolicy()).isEqualTo(CategoryPolicy.ALLOW);
    }

    @Test
    void WATCH_정책이고_티니등급이_3등급_이하면_부모에게도_알림이_발송된다() {
        Long memberId = 2L;
        Long categoryId = 5L;
        Long walletId = 10L;
        Long parentId = 1L;

        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .orderId("ORDER-001")
                .idempotencyKey("IDEMP-001")
                .password("123456")
                .build();

        given(paymentMapper.selectByIdempotencyKey("IDEMP-001")).willReturn(null);
        given(orderStore.find("ORDER-001")).willReturn(
                OrderVO.builder().merchantName("PC방").categoryId(categoryId).amount(5000L).build());

        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId))
                .willReturn(createCategoryPolicyVO(100L, "PC방", CategoryPolicy.WATCH));

        given(walletMapper.selectMemberWalletByMemberId(memberId))
                .willReturn(createWalletVO(walletId, memberId, 50000L));

        stubInsertAssignsId(1000L);
        given(paymentMapper.selectById(1000L)).willReturn(PaymentVO.builder()
                .id(1000L).walletId(walletId).categoryId(categoryId)
                .merchantName("PC방").amount(5000L).createdAt(LocalDateTime.now())
                .build());

        MemberVO childVO = new MemberVO();
        childVO.setName("김첫째");
        given(memberMapper.selectById(memberId)).willReturn(childVO);

        TeenyScoreGradeVO grade = new TeenyScoreGradeVO();
        grade.setGradeId(3L);
        given(teenyScoreMapper.selectTeenyScoreGradeByChildId(memberId)).willReturn(grade);

        MemberParentVO parentVO = new MemberParentVO();
        parentVO.setParentId(parentId);
        given(memberMapper.selectActiveParentByChildId(memberId)).willReturn(parentVO);

        given(walletMapper.selectWalletForUpdate(walletId)).willReturn(createWalletVO(walletId, memberId, 45000L));

        paymentService.progressPayment(memberId, requestDTO);

        // then: 부모에게 주의 업종 알림
        verify(notificationService).createNotification(
                eq(parentId), eq("자녀가 주의 업종에서 결제했어요"), eq("김첫째 · PC방"),
                eq(NotificationReferenceType.PAYMENT), eq(1000L), eq(true));
        // then: 자녀에게도 일반 알림
        verify(notificationService).createNotification(
                eq(memberId), eq("결제가 완료됐어요"), any(),
                eq(NotificationReferenceType.PAYMENT), eq(1000L), eq(false));
    }

    @Test
    void WATCH_정책이어도_티니등급이_4등급_이상이면_부모에게는_알림이_발송되지_않는다() {
        Long memberId = 2L;
        Long categoryId = 5L;
        Long walletId = 10L;

        PaymentRequestDTO requestDTO = PaymentRequestDTO.builder()
                .orderId("ORDER-001")
                .idempotencyKey("IDEMP-001")
                .password("123456")
                .build();

        given(paymentMapper.selectByIdempotencyKey("IDEMP-001")).willReturn(null);
        given(orderStore.find("ORDER-001")).willReturn(
                OrderVO.builder().merchantName("PC방").categoryId(categoryId).amount(5000L).build());

        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId))
                .willReturn(createCategoryPolicyVO(100L, "PC방", CategoryPolicy.WATCH));

        given(walletMapper.selectMemberWalletByMemberId(memberId))
                .willReturn(createWalletVO(walletId, memberId, 50000L));

        stubInsertAssignsId(1000L);
        given(paymentMapper.selectById(1000L)).willReturn(PaymentVO.builder()
                .id(1000L).walletId(walletId).categoryId(categoryId)
                .merchantName("PC방").amount(5000L).createdAt(LocalDateTime.now())
                .build());

        MemberVO childVO = new MemberVO();
        childVO.setName("김첫째");
        given(memberMapper.selectById(memberId)).willReturn(childVO);

        TeenyScoreGradeVO grade = new TeenyScoreGradeVO();
        grade.setGradeId(4L);
        given(teenyScoreMapper.selectTeenyScoreGradeByChildId(memberId)).willReturn(grade);

        given(walletMapper.selectWalletForUpdate(walletId)).willReturn(createWalletVO(walletId, memberId, 45000L));

        paymentService.progressPayment(memberId, requestDTO);

        // 부모 조회 자체를 금지하지 않는다. selectActiveParentByChildId는 이제 두 가지 일에
        // 쓰인다 - 부모에게 알림을 보낼지 정할 때(조건부), 그리고 부모 화면의 자녀 잔액을
        // 갱신시킬 때(매 결제). 후자는 알림 조건과 무관하게 항상 부모를 찾으므로,
        // 이 메서드가 불렸는지로 "알림이 갔는지"를 판단할 수 없다.
        //
        // 이 테스트가 실제로 주장하는 것은 '알림이 자녀에게만 간다'이므로 그것을 직접 검증한다.
        verify(notificationService, Mockito.times(1))
                .createNotification(eq(memberId), any(), any(), any(), any(), any());
    }
}
