package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.dto.request.QuestRejectRequestDTO;
import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.AfterDeadlineAction;
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
import static org.mockito.ArgumentMatchers.nullable;
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
        when(questMapper.updateVerificationReview(
                eq(VERIFICATION_ID), eq(QUEST_ID), eq("REJECTED"),
                any(), eq(NOW))).thenReturn(1);
        when(questMapper.updateAfterRejectionByParent(
                eq(QUEST_ID), eq(PARENT_ID), any(), any(),
                nullable(LocalDateTime.class), nullable(LocalDateTime.class),
                eq(NOW))).thenReturn(1);
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

    @Test
    @DisplayName("기한 전 반려는 사유를 정리하고 남은 횟수를 줄여 다시 수행 상태로 연다")
    void rejectBeforeDeadlineReopensQuest() {
        service.reject(parent(), QUEST_ID, VERIFICATION_ID,
                rejectRequest("  사진이 흐려요  ", null, null));

        verify(questMapper).updateVerificationReview(
                VERIFICATION_ID, QUEST_ID, "REJECTED", "사진이 흐려요", NOW);
        verify(questMapper).updateAfterRejectionByParent(
                QUEST_ID, PARENT_ID, QuestStatus.IN_PROGRESS, 2,
                null, null, NOW);
        verify(transferService, never()).transferInExistingTransaction(
                any(), any(), any(), any(), any());
        verify(teenyScoreChangeService, never()).change(any());
    }

    @Test
    @DisplayName("기한이 지난 반려는 부모가 고른 미래 기한으로 연장한다")
    void rejectAfterDeadlineExtendsWhenRequested() {
        when(questMapper.selectByIdForUpdateByParent(QUEST_ID, PARENT_ID))
                .thenReturn(pendingQuestWithDeadline(
                        3_000L, true, 3, NOW.minusSeconds(1)));
        LocalDateTime extendedDeadline = NOW.plusDays(7);

        service.reject(parent(), QUEST_ID, VERIFICATION_ID,
                rejectRequest("다시 인증해 주세요",
                        AfterDeadlineAction.EXTEND, extendedDeadline));

        verify(questMapper).updateAfterRejectionByParent(
                QUEST_ID, PARENT_ID, QuestStatus.IN_PROGRESS, 2,
                extendedDeadline, null, NOW);
        verify(teenyScoreChangeService, never()).change(any());
    }

    @Test
    @DisplayName("기한이 지난 반려에서 실패를 고르면 남은 횟수와 무관하게 최종 실패 처리한다")
    void rejectAfterDeadlineFailsWhenRequested() {
        when(questMapper.selectByIdForUpdateByParent(QUEST_ID, PARENT_ID))
                .thenReturn(pendingQuestWithDeadline(
                        0L, true, 3, NOW.minusDays(1)));

        service.reject(parent(), QUEST_ID, VERIFICATION_ID,
                rejectRequest("기한이 지났어요",
                        AfterDeadlineAction.FAIL, null));

        verify(questMapper).updateAfterRejectionByParent(
                QUEST_ID, PARENT_ID, QuestStatus.FAILED, 2,
                null, NOW, NOW);
        assertFinalFailureScoreApplied();
    }

    @Test
    @DisplayName("마지막 인증 기회를 반려하면 기한과 선택값에 관계없이 최종 실패한다")
    void thirdRejectionAlwaysFails() {
        when(questMapper.selectByIdForUpdateByParent(QUEST_ID, PARENT_ID))
                .thenReturn(pendingQuestWithDeadline(
                        0L, true, 1, NOW.plusDays(1)));

        service.reject(parent(), QUEST_ID, VERIFICATION_ID,
                rejectRequest("세 번째 인증도 부족해요",
                        AfterDeadlineAction.EXTEND, NOW.minusYears(2)));

        verify(questMapper).updateAfterRejectionByParent(
                QUEST_ID, PARENT_ID, QuestStatus.FAILED, 0,
                null, NOW, NOW);
        assertFinalFailureScoreApplied();
    }

    @Test
    @DisplayName("기한 후 재시도 기회가 남았는데 처리 방법이 없으면 요청을 거부한다")
    void rejectAfterDeadlineRequiresAction() {
        when(questMapper.selectByIdForUpdateByParent(QUEST_ID, PARENT_ID))
                .thenReturn(pendingQuestWithDeadline(
                        0L, true, 3, NOW.minusSeconds(1)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reject(parent(), QUEST_ID, VERIFICATION_ID,
                        rejectRequest("확인이 안 돼요", null, null)));

        assertEquals(QuestErrorCode.QUEST_REVIEW_REQUEST_INVALID,
                exception.getErrorCode());
        verify(questMapper, never()).updateVerificationReview(
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("연장 기한은 현재보다 미래이고 1년 이내여야 한다")
    void rejectValidatesExtendedDeadlineRange() {
        when(questMapper.selectByIdForUpdateByParent(QUEST_ID, PARENT_ID))
                .thenReturn(pendingQuestWithDeadline(
                        0L, true, 3, NOW.minusSeconds(1)));

        BusinessException notFuture = assertThrows(BusinessException.class,
                () -> service.reject(parent(), QUEST_ID, VERIFICATION_ID,
                        rejectRequest("연장", AfterDeadlineAction.EXTEND, NOW)));
        assertEquals(QuestErrorCode.QUEST_EXTENDED_DEADLINE_INVALID,
                notFuture.getErrorCode());

        BusinessException tooFar = assertThrows(BusinessException.class,
                () -> service.reject(parent(), QUEST_ID, VERIFICATION_ID,
                        rejectRequest("연장", AfterDeadlineAction.EXTEND,
                                NOW.plusYears(1).plusSeconds(1))));
        assertEquals(QuestErrorCode.QUEST_EXTENDED_DEADLINE_INVALID,
                tooFar.getErrorCode());
    }

    @Test
    @DisplayName("기한 전에는 기한 후 선택값을 받을 수 없고 마감 시각은 아직 기한 전이다")
    void rejectBeforeOrAtDeadlineForbidsAfterDeadlineAction() {
        when(questMapper.selectByIdForUpdateByParent(QUEST_ID, PARENT_ID))
                .thenReturn(pendingQuestWithDeadline(0L, true, 3, NOW));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.reject(parent(), QUEST_ID, VERIFICATION_ID,
                        rejectRequest("반려", AfterDeadlineAction.FAIL, null)));

        assertEquals(QuestErrorCode.QUEST_REVIEW_REQUEST_INVALID,
                exception.getErrorCode());
    }

    @Test
    @DisplayName("반려 사유는 공백 제거 후 1자 이상 500자 이하여야 한다")
    void rejectValidatesNormalizedReason() {
        BusinessException blank = assertThrows(BusinessException.class,
                () -> service.reject(parent(), QUEST_ID, VERIFICATION_ID,
                        rejectRequest("   ", null, null)));
        assertEquals(QuestErrorCode.QUEST_REVIEW_REQUEST_INVALID,
                blank.getErrorCode());

        BusinessException tooLong = assertThrows(BusinessException.class,
                () -> service.reject(parent(), QUEST_ID, VERIFICATION_ID,
                        rejectRequest("가".repeat(501), null, null)));
        assertEquals(QuestErrorCode.QUEST_REVIEW_REQUEST_INVALID,
                tooLong.getErrorCode());
        verify(questMapper, never())
                .selectByIdForUpdateByParent(any(), any());
    }

    @Test
    @DisplayName("점수 비대상 퀘스트의 최종 실패는 점수를 바꾸지 않는다")
    void finalFailureSkipsDisabledScore() {
        when(questMapper.selectByIdForUpdateByParent(QUEST_ID, PARENT_ID))
                .thenReturn(pendingQuestWithDeadline(
                        0L, false, 1, NOW.plusDays(1)));

        service.reject(parent(), QUEST_ID, VERIFICATION_ID,
                rejectRequest("최종 반려", null, null));

        verify(teenyScoreChangeService, never()).change(any());
    }

    private QuestVO pendingQuest(Long rewardAmount, boolean scoreEnabled) {
        return pendingQuestWithDeadline(
                rewardAmount,
                scoreEnabled,
                3,
                LocalDateTime.of(2026, 8, 13, 20, 0));
    }

    private QuestVO pendingQuestWithDeadline(
            Long rewardAmount,
            boolean scoreEnabled,
            int remainingCount,
            LocalDateTime deadline) {
        return QuestVO.builder()
                .id(QUEST_ID)
                .parentId(PARENT_ID)
                .childId(CHILD_ID)
                .deadline(deadline)
                .rewardAmount(rewardAmount)
                .teenyScoreEnabled(scoreEnabled)
                .status(QuestStatus.PENDING)
                .remainingCount(remainingCount)
                .build();
    }

    private QuestRejectRequestDTO rejectRequest(
            String reason,
            AfterDeadlineAction action,
            LocalDateTime extendedDeadline) {
        return QuestRejectRequestDTO.builder()
                .reason(reason)
                .afterDeadlineAction(action)
                .extendedDeadline(extendedDeadline)
                .build();
    }

    private void assertFinalFailureScoreApplied() {
        ArgumentCaptor<TeenyScoreChangeRequestDTO> scoreCaptor =
                ArgumentCaptor.forClass(TeenyScoreChangeRequestDTO.class);
        verify(teenyScoreChangeService).change(scoreCaptor.capture());
        assertEquals(-2, scoreCaptor.getValue().getAmount());
        assertEquals("QUEST_FAILED:104",
                scoreCaptor.getValue().getEventKey());
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
