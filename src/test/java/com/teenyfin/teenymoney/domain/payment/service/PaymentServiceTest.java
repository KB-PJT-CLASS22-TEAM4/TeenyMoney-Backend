package com.teenyfin.teenymoney.domain.payment.service;

import com.teenyfin.teenymoney.domain.categoryPolicy.mapper.CategoryPolicyMapper;
import com.teenyfin.teenymoney.domain.categoryPolicy.vo.CategoryPolicyVO;
import com.teenyfin.teenymoney.domain.payment.dto.request.PaymentQrRequestDTO;
import com.teenyfin.teenymoney.domain.payment.dto.response.PaymentQrResponseDTO;
import com.teenyfin.teenymoney.domain.payment.mapper.PaymentMapper;
import com.teenyfin.teenymoney.domain.payment.vo.OrderVO;
import com.teenyfin.teenymoney.domain.paymentPassword.service.PaymentPasswordService;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.WalletLedgerService;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PaymentServiceGetPaymentInfoTest {

    private final PaymentMapper paymentMapper = Mockito.mock(PaymentMapper.class);
    private final CategoryPolicyMapper categoryPolicyMapper = Mockito.mock(CategoryPolicyMapper.class);
    private final WalletMapper walletMapper = Mockito.mock(WalletMapper.class);
    private final WalletLedgerService walletLedgerService = Mockito.mock(WalletLedgerService.class);
    private final PaymentPasswordService paymentPasswordService = Mockito.mock(PaymentPasswordService.class);
    private final OrderStore orderStore = Mockito.mock(OrderStore.class);
    private final PaymentService paymentService = new PaymentService(
            paymentMapper, categoryPolicyMapper, walletMapper,
            walletLedgerService, paymentPasswordService, orderStore);

    private WalletVO createWalletVO(Long id, Long balance) {
        WalletVO vo = new WalletVO();
        vo.setId(id);
        vo.setBalance(balance);
        return vo;
    }

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

        WalletVO walletVO = createWalletVO(10L, 50000L);
        given(walletMapper.selectMemberWalletByMemberId(memberId)).willReturn(walletVO);

        CategoryPolicyVO categoryPolicyVO = CategoryPolicyVO.builder()
                .id(100L)
                .categoryName("편의점")
                .policy("ALLOW")
                .build();
        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId)).willReturn(categoryPolicyVO);

        PaymentQrResponseDTO result = paymentService.getPaymentInfo(memberId, requestDTO);

        ArgumentCaptor<OrderVO> captor = ArgumentCaptor.forClass(OrderVO.class);
        verify(orderStore).save(org.mockito.ArgumentMatchers.eq("ORDER-001"), captor.capture(), any(Duration.class));

        OrderVO savedOrderVO = captor.getValue();
        assertThat(savedOrderVO.getMerchantName()).isEqualTo("CU 강남역점");
        assertThat(savedOrderVO.getCategoryId()).isEqualTo(categoryId);
        assertThat(savedOrderVO.getAmount()).isEqualTo(3000L);

        assertThat(result.getOrderId()).isEqualTo("ORDER-001");
        assertThat(result.getBalance()).isEqualTo(50000L);
        assertThat(result.getCategoryPolicy().getPolicy()).isEqualTo("ALLOW");
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

        WalletVO walletVO = createWalletVO(10L, 50000L);
        given(walletMapper.selectMemberWalletByMemberId(memberId)).willReturn(walletVO);

        CategoryPolicyVO categoryPolicyVO = CategoryPolicyVO.builder()
                .id(100L)
                .categoryName("편의점")
                .policy("ALLOW")
                .build();
        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId)).willReturn(categoryPolicyVO);

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

        WalletVO walletVO = createWalletVO(10L, 1000L);
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

        WalletVO walletVO = createWalletVO(10L, 50000L);
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

        WalletVO walletVO = createWalletVO(10L, 50000L);
        given(walletMapper.selectMemberWalletByMemberId(memberId)).willReturn(walletVO);

        CategoryPolicyVO categoryPolicyVO = CategoryPolicyVO.builder()
                .id(100L)
                .categoryName("PC방")
                .policy("WATCH")
                .build();
        given(categoryPolicyMapper.selectByCategoryIdAndChildId(categoryId, memberId)).willReturn(categoryPolicyVO);

        given(paymentMapper.countRecentTransactions(memberId, categoryId)).willReturn(5);
        given(paymentMapper.sumRecentTransactionAmount(memberId, categoryId)).willReturn(45000L);

        PaymentQrResponseDTO result = paymentService.getPaymentInfo(memberId, requestDTO);

        assertThat(result.getTotalCount()).isEqualTo(5);
        assertThat(result.getTotalAmount()).isEqualTo(45000L);
    }
}