package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.dto.request.DepositEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.LoanEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.request.SavingEnrollmentRequestDTO;
import com.teenyfin.teenymoney.domain.financialproduct.dto.response.FinancialProductEnrollmentRequestResponseDTO;
import com.teenyfin.teenymoney.domain.financialproduct.exception.FinancialProductErrorCode;
import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.*;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberParentVO;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class FinancialProductEnrollmentServiceTest {
    private static final MemberPrincipal CHILD = new MemberPrincipal(2L, "CHILD");

    private FinancialProductMapper mapper;
    private WalletMapper walletMapper;
    private NotificationService notificationService;
    private FinancialProductEnrollmentService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        MemberMapper memberMapper = mock(MemberMapper.class);
        walletMapper = mock(WalletMapper.class);
        notificationService = mock(NotificationService.class);
        service = new FinancialProductEnrollmentService(mapper,
                new FinancialProductRateCalculator(), memberMapper,
                walletMapper, notificationService);

        MemberParentVO parent = new MemberParentVO();
        parent.setParentId(1L);
        when(memberMapper.selectActiveParentByChildId(2L)).thenReturn(parent);
        MemberVO child = new MemberVO();
        child.setId(2L);
        child.setName("김첫째");
        when(memberMapper.selectById(2L)).thenReturn(child);
        FinancialProductBenefitVO benefit = new FinancialProductBenefitVO();
        benefit.setChildId(2L);
        benefit.setGradeId(2L);
        benefit.setBonusRate(new BigDecimal("1.00"));
        benefit.setLoanRate(new BigDecimal("7.00"));
        when(mapper.selectBenefitByChildId(2L)).thenReturn(benefit);

        WalletVO wallet = new WalletVO();
        wallet.setId(10L);
        wallet.setBalance(500_000L);
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet);
        when(walletMapper.selectWalletForUpdate(10L)).thenReturn(wallet);
        doAnswer(invocation -> {
            FinancialProductEnrollmentCommandVO command = invocation.getArgument(0);
            command.setId(100L);
            return 1;
        }).when(mapper).insertDepositEnrollment(any());
    }

    @Test
    @DisplayName("예금 신청은 지갑을 만들지 않고 신청 금액을 PENDING 계약에 저장한다")
    void requestDepositStoresAmountWithoutProductWallet() {
        DepositProductVO product = new DepositProductVO();
        product.setId(1L);
        product.setProductSource(FinancialProductSource.PARENT);
        product.setRate12m(new BigDecimal("3.20"));
        product.setEarlyTerminationRate(new BigDecimal("0.50"));
        product.setMinAmount(10_000L);
        product.setMaxAmount(5_000_000L);
        when(mapper.selectVisibleDepositProductById(1L, 2L)).thenReturn(product);

        FinancialProductEnrollmentRequestResponseDTO response =
                service.requestDeposit(CHILD,
                        new DepositEnrollmentRequestDTO(1L, 50_000L, 12));

        assertEquals(100L, response.getEnrollmentId());
        assertEquals("PENDING", response.getStatus());
        assertEquals(new BigDecimal("4.20"), response.getExpectedAppliedRate());
        verify(mapper).insertDepositEnrollment(argThat(command ->
                new BigDecimal("3.20").equals(
                        command.getAppliedEarlyTerminationRate())
                        && command.getWalletId() == null
                        && Long.valueOf(50_000L).equals(command.getAmount())));
    }

    @Test
    @DisplayName("예금 가입 요청 시 부모에게 알림을 보낸다")
    void requestDepositNotifiesParent() {
        DepositProductVO product = new DepositProductVO();
        product.setId(1L);
        product.setName("티니 자유예금");
        product.setProductSource(FinancialProductSource.PARENT);
        product.setRate12m(new BigDecimal("3.20"));
        product.setEarlyTerminationRate(new BigDecimal("0.50"));
        product.setMinAmount(10_000L);
        product.setMaxAmount(5_000_000L);
        when(mapper.selectVisibleDepositProductById(1L, 2L)).thenReturn(product);

        service.requestDeposit(CHILD, new DepositEnrollmentRequestDTO(1L, 50_000L, 12));

        verify(notificationService).createNotification(
                1L, "김첫째님이 예금 가입을 요청했어요", "티니 자유예금 · 50000원",
                NotificationReferenceType.DEPOSIT_ENROLLMENT, 100L, true);
    }

    @Test
    @DisplayName("적금 가입 요청은 상품 지갑 없이 PENDING 계약만 저장한다")
    void requestSavingDoesNotCreatePendingTransfer() {
        SavingProductVO product = new SavingProductVO();
        product.setId(1L);
        product.setRate12m(new BigDecimal("3.20"));
        product.setEarlyTerminationRate(new BigDecimal("1.00"));
        product.setMinMonthAmount(1_000L);
        product.setMaxMonthAmount(500_000L);
        when(mapper.selectVisibleSavingProductById(1L, 2L)).thenReturn(product);
        doAnswer(invocation -> {
            FinancialProductEnrollmentCommandVO command = invocation.getArgument(0);
            command.setId(101L);
            return 1;
        }).when(mapper).insertSavingEnrollment(any());

        FinancialProductEnrollmentRequestResponseDTO response =
                service.requestSaving(CHILD,
                        new SavingEnrollmentRequestDTO(
                                1L, 30_000L, 12, 25, true));

        assertEquals(101L, response.getEnrollmentId());
        assertEquals("PENDING", response.getStatus());
        verify(mapper).insertSavingEnrollment(argThat(command ->
                new BigDecimal("3.20").equals(
                        command.getAppliedEarlyTerminationRate())
                        && command.getWalletId() == null));
    }

    @Test
    @DisplayName("예금 가입금액이 상품 범위를 벗어나면 가입 요청을 생성하지 않는다")
    void depositAmountOutsideProductRangeIsRejected() {
        DepositProductVO product = new DepositProductVO();
        product.setId(1L);
        product.setMinAmount(10_000L);
        product.setMaxAmount(5_000_000L);
        when(mapper.selectVisibleDepositProductById(1L, 2L)).thenReturn(product);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requestDeposit(CHILD,
                        new DepositEnrollmentRequestDTO(1L, 9_999L, 12)));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_INVALID_AMOUNT,
                exception.getErrorCode());
        verify(mapper, never()).insertDepositEnrollment(any());
    }

    @Test
    @DisplayName("적금 월 납입금액이 상품 범위를 벗어나면 가입 요청을 생성하지 않는다")
    void savingAmountOutsideProductRangeIsRejected() {
        SavingProductVO product = new SavingProductVO();
        product.setId(1L);
        product.setMinMonthAmount(1_000L);
        product.setMaxMonthAmount(500_000L);
        when(mapper.selectVisibleSavingProductById(1L, 2L)).thenReturn(product);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requestSaving(CHILD,
                        new SavingEnrollmentRequestDTO(
                                1L, 500_001L, 12, 25, true)));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_INVALID_AMOUNT,
                exception.getErrorCode());
        verify(mapper, never()).insertSavingEnrollment(any());
    }

    @Test
    @DisplayName("대출 신청금액이 상품 범위를 벗어나면 가입 요청을 생성하지 않는다")
    void loanAmountOutsideProductRangeIsRejected() {
        LoanProductVO product = new LoanProductVO();
        product.setId(1L);
        product.setMinAmount(10_000L);
        product.setMaxAmount(200_000L);
        when(mapper.selectVisibleLoanProductById(1L, 2L)).thenReturn(product);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requestLoan(CHILD,
                        new LoanEnrollmentRequestDTO(
                                1L, 200_001L, 12, 25, true)));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_INVALID_AMOUNT,
                exception.getErrorCode());
        verify(mapper, never()).insertLoanEnrollment(any());
    }

    @Test
    @DisplayName("부모 생성 대출은 부모가 선택하지 않은 가입기간 요청을 차단한다")
    void parentLoanRejectsUnselectedTerm() {
        LoanProductVO product = new LoanProductVO();
        product.setId(1L);
        product.setProductSource(FinancialProductSource.PARENT);
        product.setAvailable1m(true);
        product.setAvailable3m(true);
        product.setAvailable6m(false);
        product.setAvailable12m(true);
        product.setMinAmount(10_000L);
        product.setMaxAmount(200_000L);
        when(mapper.selectVisibleLoanProductById(1L, 2L)).thenReturn(product);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requestLoan(CHILD,
                        new LoanEnrollmentRequestDTO(
                                1L, 100_000L, 6, 25, true)));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_INVALID_TERM,
                exception.getErrorCode());
        verify(mapper, never()).insertLoanEnrollment(any());
    }

    @Test
    @DisplayName("부모 대출 가입 시 자녀 등급 금리 대신 부모 설정 금리 5.00%를 저장한다")
    void parentLoanEnrollmentUsesConfiguredProductRate() {
        LoanProductVO product = new LoanProductVO();
        product.setId(1L);
        product.setProductSource(FinancialProductSource.PARENT);
        product.setAvailable12m(true);
        product.setBaseRate(new BigDecimal("5.00"));
        product.setLateFeeRate(new BigDecimal("8.00"));
        product.setRequiredGradeId(2L);
        product.setMinAmount(10_000L);
        product.setMaxAmount(200_000L);
        when(mapper.selectVisibleLoanProductById(1L, 2L)).thenReturn(product);
        doAnswer(invocation -> {
            FinancialProductEnrollmentCommandVO command = invocation.getArgument(0);
            command.setId(102L);
            return 1;
        }).when(mapper).insertLoanEnrollment(any());

        FinancialProductEnrollmentRequestResponseDTO response =
                service.requestLoan(CHILD, new LoanEnrollmentRequestDTO(
                        1L, 100_000L, 12, 25, true));

        assertEquals(new BigDecimal("5.00"),
                response.getExpectedAppliedRate());
        verify(mapper).insertLoanEnrollment(argThat(command ->
                new BigDecimal("5.00").equals(command.getAppliedRate())));
    }

    @Test
    @DisplayName("부모 계정은 자녀용 금융상품 가입 요청 API를 사용할 수 없다")
    void parentCannotRequestEnrollment() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requestDeposit(new MemberPrincipal(1L, "PARENT"),
                        new DepositEnrollmentRequestDTO(1L, 50_000L, 12)));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_CHILD_ONLY,
                exception.getErrorCode());
    }

    @Test
    @DisplayName("동일 상품의 PENDING 계약이 있으면 중복 가입 요청을 차단한다")
    void duplicatePendingEnrollmentIsRejected() {
        DepositProductVO product = new DepositProductVO();
        product.setId(1L);
        product.setMinAmount(10_000L);
        product.setMaxAmount(5_000_000L);
        when(mapper.selectVisibleDepositProductById(1L, 2L)).thenReturn(product);
        when(mapper.countPendingDepositEnrollment(2L, 1L)).thenReturn(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requestDeposit(CHILD,
                        new DepositEnrollmentRequestDTO(1L, 50_000L, 12)));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_ENROLLMENT_DUPLICATED,
                exception.getErrorCode());
        verify(mapper, never()).insertDepositEnrollment(any());
    }

    @Test
    @DisplayName("월간 적용 등급이 요구등급보다 낮으면 대출 가입 요청을 차단한다")
    void insufficientGradeLoanRequestIsRejected() {
        LoanProductVO product = new LoanProductVO();
        product.setId(3L);
        product.setRequiredGradeId(3L);
        product.setMinAmount(10_000L);
        product.setMaxAmount(200_000L);
        when(mapper.selectVisibleLoanProductById(3L, 2L)).thenReturn(product);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requestLoan(CHILD,
                        new LoanEnrollmentRequestDTO(
                                3L, 100_000L, 12, 25, true)));

        assertEquals(FinancialProductErrorCode.FINANCIAL_PRODUCT_INSUFFICIENT_GRADE,
                exception.getErrorCode());
    }
}
