package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
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
    private QuestCreationService service;

    @BeforeEach
    void setUp() {
        service = new QuestCreationService(
                questMapper,
                memberMapper,
                familyAccessService,
                new QuestStatePolicy(),
                CLOCK);
        given(memberMapper.selectByIdForUpdate(1L)).willReturn(new MemberVO());
        given(questMapper.selectByCreationRequestKey(1L, REQUEST_KEY)).willReturn(List.of());
    }

    @Test
    void 여러_자녀의_독립_퀘스트를_요청_순서대로_생성한다() {
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
    void 중복_자녀는_삽입하기_전에_거절한다() {
        assertError(
                () -> service.create(parent(), request(List.of(2L, 2L)), REQUEST_KEY),
                QuestErrorCode.QUEST_CHILD_DUPLICATED);

        verify(memberMapper, never()).selectByIdForUpdate(any());
        verify(questMapper, never()).insert(any());
    }

    @Test
    void 현금과_티니점수가_모두_없으면_거절한다() {
        assertError(
                () -> service.create(
                        parent(),
                        request(List.of(2L), "제목", "내용", 0L, false, NOW.plusDays(1)),
                        REQUEST_KEY),
                QuestErrorCode.QUEST_REWARD_INVALID);
    }

    @Test
    void 현금_보상은_0원이_아니라면_최소_100원이다() {
        assertError(
                () -> service.create(
                        parent(),
                        request(List.of(2L), "제목", "내용", 99L, true, NOW.plusDays(1)),
                        REQUEST_KEY),
                QuestErrorCode.QUEST_REWARD_INVALID);
    }

    @Test
    void 전체_예상_보상_계산이_Long_범위를_넘으면_거절한다() {
        assertError(
                () -> service.create(
                        parent(),
                        request(List.of(2L, 3L), "제목", "내용", Long.MAX_VALUE, true,
                                NOW.plusDays(1)),
                        REQUEST_KEY),
                QuestErrorCode.QUEST_REWARD_INVALID);
    }

    @Test
    void 기한은_서버_현재_시각보다_미래이고_최대_1년_이내여야_한다() {
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
    void 정규_UUID가_아닌_생성_요청_키는_거절한다() {
        assertError(
                () -> service.create(parent(), request(List.of(2L)), "1-1-1-1-1"),
                QuestErrorCode.QUEST_CREATION_KEY_INVALID);
    }

    @Test
    void 부모가_아니면_퀘스트를_생성할_수_없다() {
        assertError(
                () -> service.create(new MemberPrincipal(2L, "CHILD"), request(List.of(2L)), REQUEST_KEY),
                QuestErrorCode.QUEST_PARENT_ONLY);

        verify(questMapper, never()).insert(any());
    }

    @Test
    void 연결되지_않은_자녀는_퀘스트_오류로_변환한다() {
        doThrow(new BusinessException(CommonErrorCode.AUTH_FORBIDDEN))
                .when(familyAccessService).requireChildAccess(parent(), 2L);

        assertError(
                () -> service.create(parent(), request(List.of(2L)), REQUEST_KEY),
                QuestErrorCode.QUEST_CHILD_NOT_LINKED);
        verify(questMapper, never()).insert(any());
    }

    @Test
    void 같은_키와_같은_내용의_재요청은_기존_결과를_자녀_요청_순서로_돌려준다() {
        QuestVO child2 = existing(202L, 2L);
        QuestVO child3 = existing(203L, 3L);
        given(questMapper.selectByCreationRequestKey(1L, REQUEST_KEY))
                .willReturn(List.of(child2, child3));

        List<Long> result = service.create(parent(), request(List.of(3L, 2L)), REQUEST_KEY);

        assertThat(result).containsExactly(203L, 202L);
        verify(questMapper, never()).insert(any());
    }

    @Test
    void 같은_키를_다른_내용에_사용하면_충돌로_거절한다() {
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
    void 부모의_AVAILABLE_퀘스트를_정규화한_내용으로_수정한다() {
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
    void 다른_부모의_퀘스트는_없는_퀘스트와_같은_오류로_응답한다() {
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
    void 마감과_서버_시각이_같은_초에는_같은_마감으로_수정할_수_있다() {
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
    void 잠금_후_상태가_바뀌어_수정이나_삭제가_0건이면_409다() {
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
    void 자녀는_수정하거나_삭제할_수_없다() {
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
