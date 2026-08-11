package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVerificationVO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.request.TeenyScoreChangeRequestDTO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("부모의 퀘스트 인증 심사")
class QuestReviewServiceTest {

    private static final Long QUEST_ID = 104L;
    private static final Long VERIFICATION_ID = 9L;
    private static final Long PARENT_ID = 1L;
    private static final Long CHILD_ID = 2L;
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 12, 2, 30);

    private QuestMapper questMapper;
    private WalletMapper walletMapper;
    private TransferService transferService;
    private TeenyScorePolicyService teenyScorePolicyService;
    private TeenyScoreChangeService teenyScoreChangeService;
    private QuestReviewService service;

    @BeforeEach
    void setUp() {
        questMapper = mock(QuestMapper.class);
        walletMapper = mock(WalletMapper.class);
        transferService = mock(TransferService.class);
        teenyScorePolicyService = new TeenyScorePolicyService();
        teenyScoreChangeService = mock(TeenyScoreChangeService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-11T17:30:00Z"),
                ZoneId.of("Asia/Seoul"));

        service = new QuestReviewService(
                questMapper,
                walletMapper,
                transferService,
                teenyScorePolicyService,
                teenyScoreChangeService,
                clock);

        when(questMapper.selectByIdForUpdateByParent(QUEST_ID, PARENT_ID))
                .thenReturn(pendingQuest(3_000L, true));
        when(questMapper.selectLatestVerificationForUpdate(QUEST_ID))
                .thenReturn(pendingVerification(VERIFICATION_ID));
        when(questMapper.updateVerificationReview(
                eq(VERIFICATION_ID), eq(QUEST_ID), eq("APPROVED"),
                eq(null), eq(NOW))).thenReturn(1);
        when(questMapper.updateCompletedByParent(
                QUEST_ID, PARENT_ID, NOW, NOW)).thenReturn(1);
        when(walletMapper.selectMemberWalletByMemberId(PARENT_ID))
                .thenReturn(wallet(10L, PARENT_ID));
        when(walletMapper.selectMemberWalletByMemberId(CHILD_ID))
                .thenReturn(wallet(20L, CHILD_ID));
    }

    @Test
    @DisplayName("현금과 점수가 있는 승인은 보상·점수·인증·퀘스트를 한 흐름으로 처리한다")
    void approveAppliesRewardScoreAndCompletionInOrder() {
        service.approve(parent(), QUEST_ID, VERIFICATION_ID);

        InOrder order = inOrder(
                questMapper, transferService, teenyScoreChangeService);
        order.verify(questMapper)
                .selectByIdForUpdateByParent(QUEST_ID, PARENT_ID);
        order.verify(questMapper)
                .selectLatestVerificationForUpdate(QUEST_ID);
        order.verify(transferService).transferInExistingTransaction(
                10L, 20L, 3_000L, TransferType.QUEST_REWARD,
                "QUEST_REWARD:104");
        order.verify(teenyScoreChangeService).change(any());
        order.verify(questMapper).updateVerificationReview(
                VERIFICATION_ID, QUEST_ID, "APPROVED", null, NOW);
        order.verify(questMapper).updateCompletedByParent(
                QUEST_ID, PARENT_ID, NOW, NOW);

        ArgumentCaptor<TeenyScoreChangeRequestDTO> scoreCaptor =
                ArgumentCaptor.forClass(TeenyScoreChangeRequestDTO.class);
        verify(teenyScoreChangeService).change(scoreCaptor.capture());
        assertEquals(3, scoreCaptor.getValue().getAmount());
        assertEquals("QUEST_COMPLETED:104",
                scoreCaptor.getValue().getEventKey());
    }

    @Test
    @DisplayName("0원이고 점수 대상이 아니면 송금과 점수를 모두 건너뛴다")
    void approveSkipsZeroRewardAndDisabledScore() {
        when(questMapper.selectByIdForUpdateByParent(QUEST_ID, PARENT_ID))
                .thenReturn(pendingQuest(0L, false));

        service.approve(parent(), QUEST_ID, VERIFICATION_ID);

        verify(transferService, never()).transferInExistingTransaction(
                any(), any(), any(), any(), any());
        verify(teenyScoreChangeService, never()).change(any());
        verify(questMapper).updateCompletedByParent(
                QUEST_ID, PARENT_ID, NOW, NOW);
    }

    @Test
    @DisplayName("최신 인증이 아니거나 이미 처리된 인증은 409로 거부한다")
    void approveRejectsStaleOrProcessedVerification() {
        when(questMapper.selectLatestVerificationForUpdate(QUEST_ID))
                .thenReturn(pendingVerification(10L));

        BusinessException stale = assertThrows(BusinessException.class,
                () -> service.approve(parent(), QUEST_ID, VERIFICATION_ID));
        assertEquals(QuestErrorCode.QUEST_VERIFICATION_CONFLICT,
                stale.getErrorCode());

        when(questMapper.selectLatestVerificationForUpdate(QUEST_ID))
                .thenReturn(QuestVerificationVO.builder()
                        .id(VERIFICATION_ID)
                        .questId(QUEST_ID)
                        .status("APPROVED")
                        .build());
        BusinessException processed = assertThrows(BusinessException.class,
                () -> service.approve(parent(), QUEST_ID, VERIFICATION_ID));
        assertEquals(QuestErrorCode.QUEST_VERIFICATION_CONFLICT,
                processed.getErrorCode());
    }

    @Test
    @DisplayName("부모나 자녀 지갑이 없으면 보상 승인을 중단한다")
    void approveRequiresBothRewardWallets() {
        when(walletMapper.selectMemberWalletByMemberId(PARENT_ID))
                .thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.approve(parent(), QUEST_ID, VERIFICATION_ID));

        assertEquals(WalletErrorCode.WALLET_NOT_FOUND,
                exception.getErrorCode());
        verify(questMapper, never()).updateCompletedByParent(
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("잔액 부족은 그대로 전달하고 인증과 퀘스트 상태를 바꾸지 않는다")
    void approvePreservesPendingStateWhenBalanceIsInsufficient() {
        when(transferService.transferInExistingTransaction(
                any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(
                        WalletErrorCode.INSUFFICIENT_BALANCE));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.approve(parent(), QUEST_ID, VERIFICATION_ID));

        assertEquals(WalletErrorCode.INSUFFICIENT_BALANCE,
                exception.getErrorCode());
        verify(questMapper, never()).updateVerificationReview(
                any(), any(), any(), any(), any());
        verify(questMapper, never()).updateCompletedByParent(
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("부모가 아니면 승인할 수 없다")
    void approveRequiresParentRole() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.approve(
                        new MemberPrincipal(CHILD_ID, "CHILD"),
                        QUEST_ID,
                        VERIFICATION_ID));

        assertEquals(QuestErrorCode.QUEST_PARENT_ONLY,
                exception.getErrorCode());
    }

    private QuestVO pendingQuest(Long rewardAmount, boolean scoreEnabled) {
        return QuestVO.builder()
                .id(QUEST_ID)
                .parentId(PARENT_ID)
                .childId(CHILD_ID)
                .deadline(LocalDateTime.of(2026, 8, 13, 20, 0))
                .rewardAmount(rewardAmount)
                .teenyScoreEnabled(scoreEnabled)
                .status(QuestStatus.PENDING)
                .remainingCount(3)
                .build();
    }

    private QuestVerificationVO pendingVerification(Long id) {
        return QuestVerificationVO.builder()
                .id(id)
                .questId(QUEST_ID)
                .attemptNo(1)
                .status("PENDING")
                .build();
    }

    private WalletVO wallet(Long id, Long memberId) {
        WalletVO wallet = new WalletVO();
        wallet.setId(id);
        wallet.setMemberId(memberId);
        return wallet;
    }

    private MemberPrincipal parent() {
        return new MemberPrincipal(PARENT_ID, "PARENT");
    }
}
