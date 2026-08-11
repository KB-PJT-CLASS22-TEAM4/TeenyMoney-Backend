package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.dto.request.QuestDeclineRequestDTO;
import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.DeclineReasonCode;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.ErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class QuestProgressServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 고정 시각을 써야 "기한이 1초 지난 상태"를 sleep 없이 만들 수 있다. NOW = 2026-08-10T10:00 (KST)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-10T01:00:00Z"), SEOUL);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 10, 0);
    private static final long QUEST_ID = 55L;
    private static final long CHILD_ID = 2L;

    private final QuestMapper questMapper = mock(QuestMapper.class);
    private QuestProgressService service;

    @BeforeEach
    void setUp() {
        service = new QuestProgressService(questMapper, new QuestStatePolicy(), CLOCK);
    }

    @Test
    @DisplayName("수락은 AVAILABLE 퀘스트를 IN_PROGRESS로 바꾼다")
    void acceptMovesAvailableQuestToInProgress() {
        lock(quest(QuestStatus.AVAILABLE, NOW.plusDays(1)));
        given(questMapper.updateStatusByChild(
                QUEST_ID, CHILD_ID, QuestStatus.AVAILABLE, QuestStatus.IN_PROGRESS, NOW))
                .willReturn(1);

        service.accept(child(), QUEST_ID);

        verify(questMapper).updateStatusByChild(
                QUEST_ID, CHILD_ID, QuestStatus.AVAILABLE, QuestStatus.IN_PROGRESS, NOW);
    }

    @Test
    @DisplayName("부모는 수락할 수 없고 퀘스트를 조회조차 하지 않는다")
    void parentCannotAcceptAndNeverReadsQuest() {
        assertError(() -> service.accept(new MemberPrincipal(1L, "PARENT"), QUEST_ID),
                QuestErrorCode.QUEST_CHILD_ONLY);

        verify(questMapper, never()).selectByIdForUpdateByChild(any(), any());
    }

    @Test
    @DisplayName("본인에게 배정되지 않은 퀘스트와 없는 퀘스트는 같은 404다")
    void otherChildsQuestAndMissingQuestReturnSame404() {
        given(questMapper.selectByIdForUpdateByChild(QUEST_ID, CHILD_ID)).willReturn(null);

        assertError(() -> service.accept(child(), QUEST_ID),
                QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);
    }

    @Test
    @DisplayName("이미 수락한 퀘스트는 다시 수락할 수 없다")
    void alreadyAcceptedQuestCannotBeAcceptedAgain() {
        lock(quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1)));

        assertError(() -> service.accept(child(), QUEST_ID),
                QuestErrorCode.QUEST_STATUS_CONFLICT);

        verify(questMapper, never()).updateStatusByChild(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("기한이 지난 퀘스트는 수락할 수 없고 상태를 EXPIRED로 바꾸지도 않는다")
    void deadlinePassedQuestCannotBeAcceptedAndIsNotExpiredHere() {
        lock(quest(QuestStatus.AVAILABLE, NOW.minusSeconds(1)));

        assertError(() -> service.accept(child(), QUEST_ID),
                QuestErrorCode.QUEST_DEADLINE_PASSED);

        // 마감 상태 변경은 스케줄러 몫이다(설계 15.2). 여기서 EXPIRED로 바꾸면 안 된다.
        verify(questMapper, never()).updateStatusByChild(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("서버 시각과 기한이 같은 초면 수락을 허용한다")
    void acceptIsAllowedOnExactDeadlineSecond() {
        lock(quest(QuestStatus.AVAILABLE, NOW));
        given(questMapper.updateStatusByChild(any(), any(), any(), any(), any())).willReturn(1);

        service.accept(child(), QUEST_ID);

        verify(questMapper).updateStatusByChild(
                QUEST_ID, CHILD_ID, QuestStatus.AVAILABLE, QuestStatus.IN_PROGRESS, NOW);
    }

    @Test
    @DisplayName("잠금 후 상태가 바뀌어 0건이 갱신되면 409다")
    void returns409WhenUpdateAffectsNoRowAfterLock() {
        lock(quest(QuestStatus.AVAILABLE, NOW.plusDays(1)));
        // 잠금과 UPDATE 사이에 상태가 바뀐 상황. WHERE 의 fromStatus 조건이 0건을 만든다.
        given(questMapper.updateStatusByChild(any(), any(), any(), any(), any())).willReturn(0);

        assertError(() -> service.accept(child(), QUEST_ID),
                QuestErrorCode.QUEST_STATUS_CONFLICT);
    }

    @Test
    @DisplayName("questId가 없거나 0 이하이면 조회하지 않고 404다")
    void invalidQuestIdReturns404WithoutQuery() {
        assertError(() -> service.accept(child(), 0L),
                QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);
        assertError(() -> service.accept(child(), null),
                QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);

        verify(questMapper, never()).selectByIdForUpdateByChild(any(), any());
    }

    // ---------- 거절 ----------

    @Test
    @DisplayName("거절은 사유와 종료 시각을 남기고 상태 전이 SQL은 쓰지 않는다")
    void declineRecordsReasonAndEndedAt() {
        lock(quest(QuestStatus.AVAILABLE, NOW.plusDays(1)));
        given(questMapper.updateDeclineByChild(
                QUEST_ID, CHILD_ID, DeclineReasonCode.NOT_ENOUGH_TIME, "학원이 있어요", NOW))
                .willReturn(1);

        service.decline(child(), QUEST_ID, decline(DeclineReasonCode.NOT_ENOUGH_TIME, "학원이 있어요"));

        verify(questMapper).updateDeclineByChild(
                QUEST_ID, CHILD_ID, DeclineReasonCode.NOT_ENOUGH_TIME, "학원이 있어요", NOW);
        // 거절은 사유와 ended_at 을 함께 써야 해서 전용 SQL 을 쓴다.
        verify(questMapper, never()).updateStatusByChild(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("상세 사유의 앞뒤 공백은 잘라서 저장한다")
    void declineTrimsSurroundingWhitespaceFromDetail() {
        lock(quest(QuestStatus.AVAILABLE, NOW.plusDays(1)));
        given(questMapper.updateDeclineByChild(any(), any(), any(), any(), any())).willReturn(1);

        service.decline(child(), QUEST_ID,
                decline(DeclineReasonCode.OTHER, "  동생이랑 하기 싫어요  "));

        verify(questMapper).updateDeclineByChild(
                QUEST_ID, CHILD_ID, DeclineReasonCode.OTHER, "동생이랑 하기 싫어요", NOW);
    }

    @Test
    @DisplayName("사유 코드가 없으면 행을 잠그기 전에 400이다")
    void declineWithoutReasonCodeFailsBeforeLocking() {
        assertError(() -> service.decline(child(), QUEST_ID, decline(null, "이유")),
                QuestErrorCode.QUEST_DECLINE_REASON_INVALID);

        // 입력이 틀린 요청 때문에 다른 요청을 잠금 대기시키지 않는다.
        verify(questMapper, never()).selectByIdForUpdateByChild(any(), any());
    }

    @Test
    @DisplayName("기타 사유는 상세 설명이 필요하고 공백만 있으면 없는 것으로 본다")
    void otherReasonRequiresDetailAndBlankCountsAsMissing() {
        assertError(() -> service.decline(child(), QUEST_ID, decline(DeclineReasonCode.OTHER, null)),
                QuestErrorCode.QUEST_DECLINE_REASON_INVALID);
        assertError(() -> service.decline(child(), QUEST_ID, decline(DeclineReasonCode.OTHER, "   ")),
                QuestErrorCode.QUEST_DECLINE_REASON_INVALID);
    }

    @Test
    @DisplayName("기타가 아닌 사유는 상세 설명 없이 거절할 수 있다")
    void nonOtherReasonNeedsNoDetail() {
        lock(quest(QuestStatus.AVAILABLE, NOW.plusDays(1)));
        given(questMapper.updateDeclineByChild(any(), any(), any(), any(), any())).willReturn(1);

        service.decline(child(), QUEST_ID, decline(DeclineReasonCode.TOO_DIFFICULT, null));

        verify(questMapper).updateDeclineByChild(
                QUEST_ID, CHILD_ID, DeclineReasonCode.TOO_DIFFICULT, null, NOW);
    }

    @Test
    @DisplayName("부모는 거절할 수 없다")
    void parentCannotDecline() {
        assertError(() -> service.decline(new MemberPrincipal(1L, "PARENT"), QUEST_ID,
                        decline(DeclineReasonCode.CANNOT_DO_NOW, null)),
                QuestErrorCode.QUEST_CHILD_ONLY);
    }

    @Test
    @DisplayName("이미 수락한 퀘스트는 거절할 수 없다")
    void alreadyAcceptedQuestCannotBeDeclined() {
        lock(quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1)));

        assertError(() -> service.decline(child(), QUEST_ID,
                        decline(DeclineReasonCode.CANNOT_DO_NOW, null)),
                QuestErrorCode.QUEST_STATUS_CONFLICT);

        verify(questMapper, never()).updateDeclineByChild(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("기한이 지난 퀘스트는 거절할 수 없고 상태를 EXPIRED로 바꾸지도 않는다")
    void deadlinePassedQuestCannotBeDeclined() {
        lock(quest(QuestStatus.AVAILABLE, NOW.minusSeconds(1)));

        assertError(() -> service.decline(child(), QUEST_ID,
                        decline(DeclineReasonCode.CANNOT_DO_NOW, null)),
                QuestErrorCode.QUEST_DEADLINE_PASSED);

        verify(questMapper, never()).updateDeclineByChild(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("잠금 후 상태가 바뀌어 0건이 갱신되면 409다")
    void declineReturns409WhenUpdateAffectsNoRow() {
        lock(quest(QuestStatus.AVAILABLE, NOW.plusDays(1)));
        given(questMapper.updateDeclineByChild(any(), any(), any(), any(), any())).willReturn(0);

        assertError(() -> service.decline(child(), QUEST_ID,
                        decline(DeclineReasonCode.HARD_TO_VERIFY, null)),
                QuestErrorCode.QUEST_STATUS_CONFLICT);
    }

    private MemberPrincipal child() {
        return new MemberPrincipal(CHILD_ID, "CHILD");
    }

    private QuestDeclineRequestDTO decline(DeclineReasonCode code, String detail) {
        return QuestDeclineRequestDTO.builder().reasonCode(code).reasonDetail(detail).build();
    }

    private QuestVO quest(QuestStatus status, LocalDateTime deadline) {
        return QuestVO.builder()
                .id(QUEST_ID)
                .parentId(1L)
                .childId(CHILD_ID)
                .status(status)
                .deadline(deadline)
                .remainingCount(3)
                .build();
    }

    private void lock(QuestVO quest) {
        given(questMapper.selectByIdForUpdateByChild(QUEST_ID, CHILD_ID)).willReturn(quest);
    }

    private void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
