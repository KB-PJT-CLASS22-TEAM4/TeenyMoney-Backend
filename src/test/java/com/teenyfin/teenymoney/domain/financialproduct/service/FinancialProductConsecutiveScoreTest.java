package com.teenyfin.teenymoney.domain.financialproduct.service;

import com.teenyfin.teenymoney.domain.financialproduct.mapper.FinancialProductMapper;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductEnrollmentVO;
import com.teenyfin.teenymoney.domain.financialproduct.vo.FinancialProductMaturityVO;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreEventRecordVO;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 만기 처리 시 연속만기 보너스가 언제 붙고 언제 안 붙는지를 검증한다. */
class FinancialProductConsecutiveScoreTest {
    private FinancialProductMapper mapper;
    private WalletMapper walletMapper;
    private TransferService transferService;
    private TeenyScoreChangeService scoreChangeService;
    private TeenyScoreMapper teenyScoreMapper;
    private NotificationService notificationService;
    private FinancialProductMaturityProcessor processor;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancialProductMapper.class);
        walletMapper = mock(WalletMapper.class);
        transferService = mock(TransferService.class);
        scoreChangeService = mock(TeenyScoreChangeService.class);
        teenyScoreMapper = mock(TeenyScoreMapper.class);
        notificationService = mock(NotificationService.class);
        MemberMapper memberMapper = mock(MemberMapper.class);
        MemberVO child = new MemberVO();
        child.setName("테스트자녀");
        when(memberMapper.selectById(2L)).thenReturn(child);
        processor = new FinancialProductMaturityProcessor(
                mapper, walletMapper, transferService,
                new TeenyScorePolicyService(), scoreChangeService,
                new FinancialProductInterestCalculator(),
                notificationService, memberMapper, teenyScoreMapper);

        LocalDate date = LocalDate.of(2027, 1, 1);
        FinancialProductMaturityVO maturity = deposit12mMaturity();
        when(mapper.selectDepositMaturityForUpdate(7L, date)).thenReturn(maturity);
        when(walletMapper.selectWalletForUpdate(20L)).thenReturn(wallet(20L, 100_000L));
        when(walletMapper.selectMemberWalletByMemberId(2L)).thenReturn(wallet(10L, 0L));
        when(walletMapper.selectMemberWalletByMemberId(1L)).thenReturn(wallet(11L, 1_000_000L));
        when(mapper.markDepositMatured(7L)).thenReturn(1);
    }

    @Test
    @DisplayName("직전에도 6개월 이상 예금을 정상 만기했으면 연속만기 보너스를 반영한다")
    void appliesBonusWhenPreviousEventWasLongTermMaturity() {
        when(teenyScoreMapper.selectRecentFinalSavingEvents(2L, 1))
                .thenReturn(List.of(finalEvent("DEPOSIT_MATURED", "DEPOSIT_ENROLLMENT", 6L)));
        FinancialProductEnrollmentVO previous = new FinancialProductEnrollmentVO();
        previous.setTermMonths(6);
        when(mapper.selectDepositEnrollmentByChildIdAndId(2L, 6L)).thenReturn(previous);

        processor.processDeposit(7L, LocalDate.of(2027, 1, 1));

        verify(scoreChangeService).change(argThat(request ->
                "SAVING_CONSECUTIVE_MATURITY:7".equals(request.getEventKey())));
        verify(notificationService).createNotification(
                2L,
                "연속 만기 보너스를 받았어요",
                "6개월 이상 예·적금 상품을 2회 연속 만기해 티니점수 10점을 받았어요.",
                NotificationReferenceType.DEPOSIT_MATURITY,
                7L, true);
    }

    @Test
    @DisplayName("직전 만기에서 연속만기 보너스를 받았으면 카운트를 초기화하고 이번 만기에는 보너스를 반영하지 않는다")
    void resetsCountAfterPreviousMaturityReceivedBonus() {
        when(teenyScoreMapper.selectRecentFinalSavingEvents(2L, 1))
                .thenReturn(List.of(finalEvent("DEPOSIT_MATURED", "DEPOSIT_ENROLLMENT", 6L)));
        FinancialProductEnrollmentVO previous = new FinancialProductEnrollmentVO();
        previous.setTermMonths(6);
        when(mapper.selectDepositEnrollmentByChildIdAndId(2L, 6L)).thenReturn(previous);
        when(teenyScoreMapper.existsHistoryByEventKey(
                2L, "SAVING_CONSECUTIVE_MATURITY:6")).thenReturn(true);

        processor.processDeposit(7L, LocalDate.of(2027, 1, 1));

        verify(scoreChangeService, never()).change(argThat(request ->
                request.getEventKey().startsWith("SAVING_CONSECUTIVE_MATURITY")));
        verify(notificationService, never()).createNotification(
                2L,
                "연속 만기 보너스를 받았어요",
                "6개월 이상 예·적금 상품을 2회 연속 만기해 티니점수 10점을 받았어요.",
                NotificationReferenceType.DEPOSIT_MATURITY,
                7L, true);
    }

    @Test
    @DisplayName("직전 이벤트가 중도해지였으면 연속만기 보너스를 반영하지 않는다")
    void skipsBonusWhenPreviousEventWasEarlyTermination() {
        when(teenyScoreMapper.selectRecentFinalSavingEvents(2L, 1))
                .thenReturn(List.of(finalEvent("DEPOSIT_EARLY_TERMINATED", "DEPOSIT_ENROLLMENT", 6L)));

        processor.processDeposit(7L, LocalDate.of(2027, 1, 1));

        verify(scoreChangeService, never()).change(argThat(request ->
                request.getEventKey().startsWith("SAVING_CONSECUTIVE_MATURITY")));
        verify(mapper, never()).selectDepositEnrollmentByChildIdAndId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("직전 만기 상품이 6개월 미만이었으면 연속만기 보너스를 반영하지 않는다")
    void skipsBonusWhenPreviousMaturityWasShortTerm() {
        when(teenyScoreMapper.selectRecentFinalSavingEvents(2L, 1))
                .thenReturn(List.of(finalEvent("DEPOSIT_MATURED", "DEPOSIT_ENROLLMENT", 6L)));
        FinancialProductEnrollmentVO previous = new FinancialProductEnrollmentVO();
        previous.setTermMonths(3);
        when(mapper.selectDepositEnrollmentByChildIdAndId(2L, 6L)).thenReturn(previous);

        processor.processDeposit(7L, LocalDate.of(2027, 1, 1));

        verify(scoreChangeService, never()).change(argThat(request ->
                request.getEventKey().startsWith("SAVING_CONSECUTIVE_MATURITY")));
    }

    @Test
    @DisplayName("직전 만기/해지 이력이 없으면 연속만기 보너스를 반영하지 않는다")
    void skipsBonusWhenNoPriorEventExists() {
        when(teenyScoreMapper.selectRecentFinalSavingEvents(2L, 1)).thenReturn(List.of());

        processor.processDeposit(7L, LocalDate.of(2027, 1, 1));

        verify(scoreChangeService, never()).change(argThat(request ->
                request.getEventKey().startsWith("SAVING_CONSECUTIVE_MATURITY")));
    }

    private TeenyScoreEventRecordVO finalEvent(
            String eventCode, String referenceType, Long referenceId) {
        TeenyScoreEventRecordVO event = new TeenyScoreEventRecordVO();
        event.setEventCode(eventCode);
        event.setReferenceType(referenceType);
        event.setReferenceId(referenceId);
        return event;
    }

    private FinancialProductMaturityVO deposit12mMaturity() {
        FinancialProductMaturityVO value = new FinancialProductMaturityVO();
        value.setEnrollmentId(7L);
        value.setChildId(2L);
        value.setParentId(1L);
        value.setProductWalletId(20L);
        value.setProductName("테스트 예금");
        value.setAppliedRate(new BigDecimal("3.00"));
        value.setTermMonths(12);
        value.setInterestCalculationType("SIMPLE");
        value.setStartDate(LocalDate.of(2026, 1, 1));
        value.setMaturityDate(LocalDate.of(2027, 1, 1));
        value.setStatus("ACTIVE");
        return value;
    }

    private WalletVO wallet(long id, long balance) {
        WalletVO wallet = new WalletVO();
        wallet.setId(id);
        wallet.setBalance(balance);
        return wallet;
    }
}
