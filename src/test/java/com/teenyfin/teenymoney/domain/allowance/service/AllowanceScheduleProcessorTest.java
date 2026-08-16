package com.teenyfin.teenymoney.domain.allowance.service;

import com.teenyfin.teenymoney.domain.allowance.mapper.AllowanceScheduleMapper;
import com.teenyfin.teenymoney.domain.allowance.vo.AllowanceScheduleVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferVO;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AllowanceScheduleProcessorTest {

    private AllowanceScheduleMapper mapper;
    private WalletMapper walletMapper;
    private TransferService transferService;
    private NotificationService notificationService;
    private AllowanceScheduleProcessor processor;

    @BeforeEach
    void setUp() {
        mapper = mock(AllowanceScheduleMapper.class);
        walletMapper = mock(WalletMapper.class);
        transferService = mock(TransferService.class);
        notificationService = mock(NotificationService.class);
        processor = new AllowanceScheduleProcessor(mapper, walletMapper, transferService, notificationService);
    }

    // 정상 성공하면 송금이 실제로 실행되고, 다음 주기로 next_payment_date가 갱신되고,
    // 알림은 안 가는지 (성공은 조용히 지나간다는 기획 결정 확인)
    @Test
    @DisplayName("성공하면 송금하고 다음 주기로 next_payment_date를 갱신하며, 알림은 안 보낸다")
    void successAdvancesNextPaymentDateWithoutNotification() {
        LocalDate paymentDate = LocalDate.of(2026, 8, 17); // 월요일
        AllowanceScheduleVO schedule = dueSchedule(paymentDate);
        when(mapper.selectById(5L)).thenReturn(schedule);
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(10L));
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(20L));
        TransferVO pending = new TransferVO();
        pending.setId(100L);
        when(transferService.createPendingTransfer(
                eq(10L), eq(20L), eq(10_000L), eq(TransferType.ALLOWANCE),
                eq("ALLOWANCE_SCHEDULE:5:2026-08-17")))
                .thenReturn(pending);

        processor.process(5L, paymentDate);

        verify(transferService).executeTransfer(100L);
        verify(mapper).updateNextPaymentDate(5L, LocalDate.of(2026, 8, 24));
        verifyNoInteractions(notificationService);
    }

    // (missed-cycle 분기 검증) next_payment_date가 오늘(paymentDate)보다 과거면 - 서버가
    // 그 날짜에 배치를 못 돌렸던 경우다. 이땐 송금 시도 자체를 안 하고(지갑 조회도 안 함),
    // "미지급" 알림만 보낸 뒤 다음 정상 주기로 넘어가는지 확인한다.
    @Test
    @DisplayName("next_payment_date가 오늘보다 과거면(놓친 회차) 송금 시도 없이 미지급 알림만 보내고 다음 정상 주기로 넘어간다")
    void missedCycleSkipsPaymentNotifiesAndAdvancesToNextCycle() {
        LocalDate missedDate = LocalDate.of(2026, 8, 10); // 월요일 - 원래 지급됐어야 할 날
        LocalDate paymentDate = LocalDate.of(2026, 8, 13); // 목요일 - 배치가 뒤늦게 도는 오늘
        AllowanceScheduleVO schedule = dueSchedule(missedDate); // next_payment_date=8/10로 스케줄 세팅
        when(mapper.selectById(5L)).thenReturn(schedule);

        processor.process(5L, paymentDate);

        // 송금 시도 자체가 없어야 한다 - 지갑 조회, 송금 둘 다 안 일어남
        verifyNoInteractions(walletMapper, transferService);
        // 미지급 알림은 가되, 실제 송금 시도가 없었으니 transferId는 null이어야 한다
        verify(notificationService).createNotification(
                eq(1L), anyString(), anyString(),
                eq(NotificationReferenceType.TRANSFER), eq((Long) null), eq(true));
        // 오늘(8/13, 목) 기준 다음 월요일인 8/17로 정상 갱신돼야 한다
        verify(mapper).updateNextPaymentDate(5L, LocalDate.of(2026, 8, 17));
    }

    // 잔액 부족 같은 BusinessException으로 실패하면 그 구체적인 사유로 부모에게 알림이 가고,
    // 그래도 다음 주기로 넘어가는지
    @Test
    @DisplayName("잔액 부족 등 BusinessException으로 실패하면 부모에게 구체적인 사유로 알리고 그래도 다음 주기로 넘어간다")
    void businessExceptionNotifiesParentWithReasonAndStillAdvances() {
        LocalDate paymentDate = LocalDate.of(2026, 8, 17);
        AllowanceScheduleVO schedule = dueSchedule(paymentDate);
        when(mapper.selectById(5L)).thenReturn(schedule);
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(10L));
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(20L));
        TransferVO pending = new TransferVO();
        pending.setId(100L);
        when(transferService.createPendingTransfer(any(), any(), any(), any(), anyString()))
                .thenReturn(pending);
        doThrow(new BusinessException(WalletErrorCode.INSUFFICIENT_BALANCE))
                .when(transferService).executeTransfer(100L);

        assertDoesNotThrow(() -> processor.process(5L, paymentDate));

        verify(notificationService).createNotification(
                eq(1L), anyString(), eq(WalletErrorCode.INSUFFICIENT_BALANCE.getMessage()),
                eq(NotificationReferenceType.TRANSFER), eq(100L), eq(true));
        verify(mapper).updateNextPaymentDate(5L, LocalDate.of(2026, 8, 24));
    }

    // (2번 버그 수정 검증 - 이번에 고친 핵심 테스트) BusinessException이 아닌 일반 RuntimeException이
    // 터져도 Processor 밖으로 안 던지고, 일반화된 메시지로 부모에게 알림 가고, next_payment_date도
    // 정상적으로 갱신되는지 - 이 catch 분기가 없으면 이 테스트는 예외가 그대로 튀어나가서 실패한다
    @Test
    @DisplayName("BusinessException이 아닌 예상 못한 RuntimeException이 나도 안 던지고, 일반화된 메시지로 알리고 다음 주기로 넘어간다")
    void unexpectedRuntimeExceptionNotifiesParentWithGenericMessageAndStillAdvances() {
        LocalDate paymentDate = LocalDate.of(2026, 8, 17);
        AllowanceScheduleVO schedule = dueSchedule(paymentDate);
        when(mapper.selectById(5L)).thenReturn(schedule);
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(10L));
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(20L));
        TransferVO pending = new TransferVO();
        pending.setId(100L);
        when(transferService.createPendingTransfer(any(), any(), any(), any(), anyString()))
                .thenReturn(pending);
        // BusinessException이 아닌 일반 RuntimeException - 예: DB 순간 장애 같은 예상 밖 상황
        doThrow(new RuntimeException("DB 순간 장애"))
                .when(transferService).executeTransfer(100L);

        assertDoesNotThrow(() -> processor.process(5L, paymentDate));

        verify(notificationService).createNotification(
                eq(1L), anyString(), anyString(),
                eq(NotificationReferenceType.TRANSFER), eq(100L), eq(true));
        // 예외가 나도 마지막 next_payment_date 갱신까지 반드시 도달해야 한다 (이번에 고친 부분)
        verify(mapper).updateNextPaymentDate(5L, LocalDate.of(2026, 8, 24));
    }

    // 송금 시도 자체(createPendingTransfer)가 실패해서 pending이 아예 안 만들어진 경우에도,
    // 알림엔 transferId=null로 보내고 next_payment_date는 정상 갱신되는지
    @Test
    @DisplayName("createPendingTransfer 자체가 실패해도(pending이 null) 예외를 안 던지고 next_payment_date는 갱신한다")
    void failureBeforePendingCreatedStillAdvances() {
        LocalDate paymentDate = LocalDate.of(2026, 8, 17);
        AllowanceScheduleVO schedule = dueSchedule(paymentDate);
        when(mapper.selectById(5L)).thenReturn(schedule);
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(10L));
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(20L));
        doThrow(new BusinessException(WalletErrorCode.INVALID_TRANSFER_AMOUNT))
                .when(transferService).createPendingTransfer(any(), any(), any(), any(), anyString());

        assertDoesNotThrow(() -> processor.process(5L, paymentDate));

        // pending이 아예 안 만들어졌으니 referenceId(transferId)는 null로 알림이 가야 한다
        verify(notificationService).createNotification(
                eq(1L), anyString(), anyString(),
                eq(NotificationReferenceType.TRANSFER), eq((Long) null), eq(true));
        verify(mapper).updateNextPaymentDate(5L, LocalDate.of(2026, 8, 24));
    }

    // 배치가 도는 사이 비활성화된 스케줄은 지갑 조회도, 송금도, 알림도 없이 조용히 건너뛰는지
    @Test
    @DisplayName("배치가 도는 사이 비활성화된 스케줄은 조용히 스킵한다")
    void inactiveScheduleIsSkipped() {
        LocalDate paymentDate = LocalDate.of(2026, 8, 17);
        AllowanceScheduleVO schedule = dueSchedule(paymentDate);
        schedule.setActive(false);
        when(mapper.selectById(5L)).thenReturn(schedule);

        processor.process(5L, paymentDate);

        verifyNoInteractions(walletMapper, transferService, notificationService);
        verify(mapper, never()).updateNextPaymentDate(any(), any());
    }

    // 배치가 도는 사이 삭제돼서 조회가 안 되는(null) 스케줄도 마찬가지로 조용히 건너뛰는지
    @Test
    @DisplayName("삭제된(조회 안 되는) 스케줄은 조용히 스킵한다")
    void missingScheduleIsSkipped() {
        LocalDate paymentDate = LocalDate.of(2026, 8, 17);
        when(mapper.selectById(5L)).thenReturn(null);

        processor.process(5L, paymentDate);

        verifyNoInteractions(walletMapper, transferService, notificationService);
        verify(mapper, never()).updateNextPaymentDate(any(), any());
    }

    private AllowanceScheduleVO dueSchedule(LocalDate paymentDate) {
        AllowanceScheduleVO schedule = new AllowanceScheduleVO();
        schedule.setId(5L);
        schedule.setParentId(1L);
        schedule.setChildId(2L);
        schedule.setAmount(10_000L);
        schedule.setCycleType("WEEKLY");
        schedule.setPaymentDay(1);
        schedule.setNextPaymentDate(paymentDate);
        schedule.setActive(true);
        return schedule;
    }

    private WalletVO wallet(Long id) {
        WalletVO wallet = new WalletVO();
        wallet.setId(id);
        return wallet;
    }
}
