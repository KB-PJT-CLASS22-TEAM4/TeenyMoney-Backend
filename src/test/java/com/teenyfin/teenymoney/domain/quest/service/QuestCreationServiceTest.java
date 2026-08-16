package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestCreateRequestDTO;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.quest.vo.VerificationRequirement;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class QuestCreationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-10T01:00:00Z"), SEOUL);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 10, 0);
    private static final String REQUEST_KEY = "11111111-1111-1111-1111-111111111111";

    private final QuestMapper questMapper = mock(QuestMapper.class);
    private final MemberMapper memberMapper = mock(MemberMapper.class);
    private final FamilyAccessService familyAccessService = mock(FamilyAccessService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private QuestCreationService service;

    @BeforeEach
    void setUp() {
        service = new QuestCreationService(
                questMapper,
                memberMapper,
                familyAccessService,
                new QuestStatePolicy(),
                notificationService,
                CLOCK);
        given(memberMapper.selectByIdForUpdate(1L)).willReturn(new MemberVO());
        given(questMapper.selectByCreationRequestKey(1L, REQUEST_KEY)).willReturn(List.of());
    }

    @Test
    @DisplayName("여러 자녀의 독립 퀘스트를 요청 순서대로 생성한다")
    void createsIndependentQuestsInRequestedChildOrder() {
        AtomicLong ids = new AtomicLong(100L);
        doAnswer(invocation -> {
            QuestVO quest = invocation.getArgument(0);
            quest.setId(ids.incrementAndGet());
            return 1;
        }).when(questMapper).insert(any(QuestVO.class));
        ArgumentCaptor<QuestVO> captor = ArgumentCaptor.forClass(QuestVO.class);

        List<Long> result = service.create(
                parent(),
                request(List.of(3L, 2L), "  방 청소  ", "  책상까지 정리  ", 1_000L, true,
                        NOW.plusDays(1)),
                REQUEST_KEY);

        verify(questMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(result).containsExactly(101L, 102L);
        assertThat(captor.getAllValues()).extracting(QuestVO::getChildId)
                .containsExactly(3L, 2L);
        assertThat(captor.getAllValues()).extracting(QuestVO::getTitle)
                .containsOnly("방 청소");
        assertThat(captor.getAllValues()).extracting(QuestVO::getContent)
                .containsOnly("책상까지 정리");
        assertThat(captor.getAllValues()).extracting(QuestVO::getStatus)
                .containsOnly(QuestStatus.AVAILABLE);
        assertThat(captor.getAllValues()).extracting(QuestVO::getRemainingCount)
                .containsOnly(3);
    }

    @Test
    @DisplayName("중복 자녀는 삽입하기 전에 거절한다")
    void rejectsDuplicateChildBeforeInsert() {
        assertError(
                () -> service.create(parent(), request(List.of(2L, 2L)), REQUEST_KEY),
                QuestErrorCode.QUEST_CHILD_DUPLICATED);

        verify(memberMapper, never()).selectByIdForUpdate(any());
        verify(questMapper, never()).insert(any());
    }

    @Test
    @DisplayName("현금과 티니점수가 모두 없으면 거절한다")
    void rejectsQuestWithNeitherCashNorTeenyScore() {
        assertError(
                () -> service.create(
                        parent(),
                        request(List.of(2L), "제목", "내용", 0L, false, NOW.plusDays(1)),
                        REQUEST_KEY),
                QuestErrorCode.QUEST_REWARD_INVALID);
    }

    @Test
    @DisplayName("현금 보상은 0원이 아니라면 최소 100원이다")
    void cashRewardIsAtLeast100WhenNotZero() {
        assertError(
                () -> service.create(
                        parent(),
                        request(List.of(2L), "제목", "내용", 99L, true, NOW.plusDays(1)),
                        REQUEST_KEY),
                QuestErrorCode.QUEST_REWARD_INVALID);
    }

    @Test
    @DisplayName("전체 예상 보상 계산이 Long 범위를 넘으면 거절한다")
    void rejectsTotalRewardOverflow() {
        assertError(
                () -> service.create(
                        parent(),
                        request(List.of(2L, 3L), "제목", "내용", Long.MAX_VALUE, true,
                                NOW.plusDays(1)),
                        REQUEST_KEY),
                QuestErrorCode.QUEST_REWARD_INVALID);
    }

    @Test
    @DisplayName("기한은 서버 현재 시각보다 미래이고 최대 1년 이내여야 한다")
    void deadlineMustBeFutureAndWithinOneYear() {
        assertError(
                () -> service.create(
                        parent(), request(List.of(2L), "제목", "내용", 100L, true, NOW), REQUEST_KEY),
                QuestErrorCode.QUEST_DEADLINE_INVALID);
        assertError(
                () -> service.create(
                        parent(),
                        request(List.of(2L), "제목", "내용", 100L, true,
                                NOW.plusYears(1).plusSeconds(1)),
                        REQUEST_KEY),
                QuestErrorCode.QUEST_DEADLINE_INVALID);
    }

    @Test
    @DisplayName("정규 UUID가 아닌 생성 요청 키는 거절한다")
    void rejectsNonCanonicalUuidRequestKey() {
        assertError(
                () -> service.create(parent(), request(List.of(2L)), "1-1-1-1-1"),
                QuestErrorCode.QUEST_CREATION_KEY_INVALID);
    }

    @Test
    @DisplayName("부모가 아니면 퀘스트를 생성할 수 없다")
    void nonParentCannotCreateQuest() {
        assertError(
                () -> service.create(new MemberPrincipal(2L, "CHILD"), request(List.of(2L)), REQUEST_KEY),
                QuestErrorCode.QUEST_PARENT_ONLY);

        verify(questMapper, never()).insert(any());
        verify(notificationService, never())
                .createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("연결되지 않은 자녀는 퀘스트 오류로 변환한다")
    void translatesUnlinkedChildToQuestError() {
        doThrow(new BusinessException(CommonErrorCode.AUTH_FORBIDDEN))
                .when(familyAccessService).requireChildAccess(parent(), 2L);

        assertError(
                () -> service.create(parent(), request(List.of(2L)), REQUEST_KEY),
                QuestErrorCode.QUEST_CHILD_NOT_LINKED);
        verify(questMapper, never()).insert(any());
    }

    @Test
    @DisplayName("같은 키와 같은 내용의 재요청은 기존 결과를 자녀 요청 순서로 돌려준다")
    void retryWithSameKeyAndBodyReturnsExistingIdsInChildOrder() {
        QuestVO child2 = existing(202L, 2L);
        QuestVO child3 = existing(203L, 3L);
        given(questMapper.selectByCreationRequestKey(1L, REQUEST_KEY))
                .willReturn(List.of(child2, child3));

        List<Long> result = service.create(parent(), request(List.of(3L, 2L)), REQUEST_KEY);

        assertThat(result).containsExactly(203L, 202L);
        verify(questMapper, never()).insert(any());
    }

    @Test
    @DisplayName("퀘스트를 생성하면 자녀마다 알림이 하나씩 발송된다")
    void sendsOneNotificationPerChildOnCreate() {
        stubGeneratedIds();

        service.create(
                parent(),
                request(List.of(3L, 2L), "방 청소", "책상까지 정리", 1_000L, true, NOW.plusDays(1)),
                REQUEST_KEY);

        verify(notificationService).createNotification(
                eq(3L), eq("새 퀘스트가 도착했어요"), eq("방 청소 · 보상 1,000원"),
                eq(NotificationReferenceType.QUEST), eq(101L), eq(true));
        verify(notificationService).createNotification(
                eq(2L), eq("새 퀘스트가 도착했어요"), eq("방 청소 · 보상 1,000원"),
                eq(NotificationReferenceType.QUEST), eq(102L), eq(true));
    }

    @Test
    @DisplayName("현금 보상이 없으면 알림 내용에 보상 문구를 붙이지 않는다")
    void omitsRewardTextWhenQuestHasNoCashReward() {
        stubGeneratedIds();

        service.create(
                parent(),
                request(List.of(2L), "방 청소", "책상까지 정리", 0L, true, NOW.plusDays(1)),
                REQUEST_KEY);

        verify(notificationService).createNotification(
                eq(2L), eq("새 퀘스트가 도착했어요"), eq("방 청소"),
                eq(NotificationReferenceType.QUEST), eq(101L), eq(true));
    }

    @Test
    @DisplayName("같은 키의 재요청은 알림을 다시 보내지 않는다")
    void retryWithSameKeyDoesNotResendNotification() {
        given(questMapper.selectByCreationRequestKey(1L, REQUEST_KEY))
                .willReturn(List.of(existing(202L, 2L)));

        service.create(parent(), request(List.of(2L)), REQUEST_KEY);

        verify(notificationService, never())
                .createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("같은 키를 다른 내용에 사용하면 충돌로 거절한다")
    void rejectsSameKeyUsedWithDifferentBody() {
        QuestVO existing = existing(202L, 2L);
        existing.setTitle("다른 제목");
        given(questMapper.selectByCreationRequestKey(1L, REQUEST_KEY))
                .willReturn(List.of(existing));

        assertError(
                () -> service.create(parent(), request(List.of(2L)), REQUEST_KEY),
                QuestErrorCode.QUEST_CREATION_REQUEST_CONFLICT);
        verify(questMapper, never()).insert(any());
    }

    @Test
    @DisplayName("부모의 AVAILABLE 퀘스트를 정규화한 내용으로 수정한다")
    void updatesAvailableQuestWithNormalizedContent() {
        QuestVO current = existing(55L, 2L);
        given(questMapper.selectByIdForUpdateByParent(55L, 1L)).willReturn(current);
        given(questMapper.updateAvailable(any(QuestVO.class))).willReturn(1);
        ArgumentCaptor<QuestVO> captor = ArgumentCaptor.forClass(QuestVO.class);

        service.update(parent(), 55L, updateRequest("  새 제목  ", "  새 내용  "));

        verify(questMapper).updateAvailable(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(55L);
        assertThat(captor.getValue().getParentId()).isEqualTo(1L);
        assertThat(captor.getValue().getTitle()).isEqualTo("새 제목");
        assertThat(captor.getValue().getContent()).isEqualTo("새 내용");
    }

    @Test
    @DisplayName("다른 부모의 퀘스트는 없는 퀘스트와 같은 오류로 응답한다")
    void otherParentsQuestReturnsSameErrorAsMissing() {
        given(questMapper.selectByIdForUpdateByParent(55L, 1L)).willReturn(null);

        assertError(
                () -> service.update(parent(), 55L, updateRequest("제목", "내용")),
                QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);

        verify(questMapper, never()).updateAvailable(any());
    }

    @Test
    void AVAILABLE_퀘스트는_물리_삭제한다() {
        QuestVO current = existing(55L, 2L);
        given(questMapper.selectByIdForUpdateByParent(55L, 1L)).willReturn(current);
        given(questMapper.deleteAvailable(55L, 1L)).willReturn(1);

        service.delete(parent(), 55L);

        verify(questMapper).deleteAvailable(55L, 1L);
    }

    @Test
    @DisplayName("마감과 서버 시각이 같은 초에는 같은 마감으로 수정할 수 있다")
    void canUpdateWithSameDeadlineOnExactDeadlineSecond() {
        QuestVO current = existing(55L, 2L);
        current.setDeadline(NOW);
        given(questMapper.selectByIdForUpdateByParent(55L, 1L)).willReturn(current);
        given(questMapper.updateAvailable(any(QuestVO.class))).willReturn(1);
        QuestUpdateRequestDTO request = QuestUpdateRequestDTO.builder()
                .title("제목")
                .content("내용")
                .deadline(NOW)
                .rewardAmount(500L)
                .teenyScoreEnabled(true)
                .verificationRequirement(VerificationRequirement.TEXT_REQUIRED)
                .build();

        service.update(parent(), 55L, request);

        verify(questMapper).updateAvailable(any(QuestVO.class));
    }

    @Test
    @DisplayName("잠금 후 상태가 바뀌어 수정이나 삭제가 0건이면 409다")
    void returns409WhenUpdateOrDeleteAffectsNoRowAfterLock() {
        QuestVO current = existing(55L, 2L);
        given(questMapper.selectByIdForUpdateByParent(55L, 1L)).willReturn(current);
        given(questMapper.updateAvailable(any(QuestVO.class))).willReturn(0);
        given(questMapper.deleteAvailable(55L, 1L)).willReturn(0);

        assertError(
                () -> service.update(parent(), 55L, updateRequest("제목", "내용")),
                QuestErrorCode.QUEST_STATUS_CONFLICT);
        assertError(
                () -> service.delete(parent(), 55L),
                QuestErrorCode.QUEST_STATUS_CONFLICT);
    }

    @Test
    @DisplayName("자녀는 수정하거나 삭제할 수 없다")
    void childCannotUpdateOrDelete() {
        MemberPrincipal child = new MemberPrincipal(2L, "CHILD");

        assertError(
                () -> service.update(child, 55L, updateRequest("제목", "내용")),
                QuestErrorCode.QUEST_PARENT_ONLY);
        assertError(
                () -> service.delete(child, 55L),
                QuestErrorCode.QUEST_PARENT_ONLY);
    }

    private MemberPrincipal parent() {
        return new MemberPrincipal(1L, "PARENT");
    }

    // insert()가 호출될 때마다 101, 102 순으로 id를 채워준다 (useGeneratedKeys 흉내)
    private void stubGeneratedIds() {
        AtomicLong ids = new AtomicLong(100L);
        doAnswer(invocation -> {
            QuestVO quest = invocation.getArgument(0);
            quest.setId(ids.incrementAndGet());
            return 1;
        }).when(questMapper).insert(any(QuestVO.class));
    }

    private QuestCreateRequestDTO request(List<Long> childIds) {
        return request(childIds, "제목", "내용", 100L, true, NOW.plusDays(1));
    }

    private QuestCreateRequestDTO request(List<Long> childIds, String title, String content,
                                          Long rewardAmount, boolean teenyScoreEnabled,
                                          LocalDateTime deadline) {
        return QuestCreateRequestDTO.builder()
                .childIds(childIds)
                .title(title)
                .content(content)
                .deadline(deadline)
                .rewardAmount(rewardAmount)
                .teenyScoreEnabled(teenyScoreEnabled)
                .verificationRequirement(VerificationRequirement.FREE)
                .build();
    }

    private QuestVO existing(Long id, Long childId) {
        return QuestVO.builder()
                .id(id)
                .parentId(1L)
                .childId(childId)
                .creationRequestKey(REQUEST_KEY)
                .title("제목")
                .content("내용")
                .deadline(NOW.plusDays(1))
                .rewardAmount(100L)
                .teenyScoreEnabled(true)
                .verificationRequirement(VerificationRequirement.FREE)
                .status(QuestStatus.AVAILABLE)
                .remainingCount(3)
                .build();
    }

    private QuestUpdateRequestDTO updateRequest(String title, String content) {
        return QuestUpdateRequestDTO.builder()
                .title(title)
                .content(content)
                .deadline(NOW.plusDays(2))
                .rewardAmount(500L)
                .teenyScoreEnabled(true)
                .verificationRequirement(VerificationRequirement.TEXT_REQUIRED)
                .build();
    }

    private void assertError(ThrowingCall call, QuestErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
