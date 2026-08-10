package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.dto.response.QuestDetailResponseDTO;
import com.teenyfin.teenymoney.domain.quest.dto.response.QuestListResponseDTO;
import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestTab;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVerificationVO;
import com.teenyfin.teenymoney.domain.quest.vo.VerificationRequirement;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import com.teenyfin.teenymoney.global.storage.S3Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class QuestQueryServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-10T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 10, 0);

    private final QuestMapper questMapper = mock(QuestMapper.class);
    private final S3Storage s3Storage = mock(S3Storage.class);
    private QuestQueryService service;

    @BeforeEach
    void setUp() {
        service = new QuestQueryService(questMapper, s3Storage, CLOCK);
    }

    @Test
    void 부모는_연결된_자녀로_좁혀_20건과_다음_커서를_받는다() {
        given(questMapper.selectPageByParent(
                eq(1L), eq(2L), anyList(), isNull(), isNull(), eq(false), eq(21)))
                .willReturn(rows(21, QuestStatus.AVAILABLE));

        QuestListResponseDTO result = service.getQuests(
                parent(), QuestTab.AVAILABLE, 2L, null);

        assertThat(result.getItems()).hasSize(20);
        assertThat(result.getNextCursor()).isNotBlank();
        verify(questMapper).selectPageByParent(
                eq(1L), eq(2L), eq(List.of(QuestStatus.AVAILABLE)),
                isNull(), isNull(), eq(false), eq(21));
    }

    @Test
    void 자녀가_childId_필터를_보내면_400_오류다() {
        assertError(
                () -> service.getQuests(child(), QuestTab.AVAILABLE, 3L, null),
                CommonErrorCode.COMMON_INVALID_INPUT);

        verify(questMapper, never()).selectPageByChild(
                eq(2L), anyList(), isNull(), isNull(), eq(false), eq(21));
    }

    @Test
    void 자녀_목록은_인증된_자녀_ID로만_조회한다() {
        given(questMapper.selectPageByChild(
                eq(2L), anyList(), isNull(), isNull(), eq(false), eq(21)))
                .willReturn(List.of(row(1L, QuestStatus.IN_PROGRESS)));

        QuestListResponseDTO result = service.getQuests(
                child(), QuestTab.ONGOING, null, null);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getNextCursor()).isNull();
        verify(questMapper).selectPageByChild(
                eq(2L), eq(List.of(QuestStatus.IN_PROGRESS, QuestStatus.PENDING)),
                isNull(), isNull(), eq(false), eq(21));
    }

    @Test
    void 다음_커서는_같은_탭에서만_사용할_수_있다() {
        given(questMapper.selectPageByParent(
                eq(1L), isNull(), anyList(), isNull(), isNull(), eq(false), eq(21)))
                .willReturn(rows(21, QuestStatus.AVAILABLE));
        String cursor = service.getQuests(parent(), QuestTab.AVAILABLE, null, null)
                .getNextCursor();

        assertError(
                () -> service.getQuests(parent(), QuestTab.COMPLETED, null, cursor),
                CommonErrorCode.COMMON_INVALID_INPUT);
    }

    @Test
    void 깨진_커서는_400_오류다() {
        assertError(
                () -> service.getQuests(parent(), QuestTab.AVAILABLE, null, "not-a-cursor"),
                CommonErrorCode.COMMON_INVALID_INPUT);
    }

    @Test
    void 부모_상세는_부모_범위_SQL을_사용하고_프로필_URL을_만든다() {
        QuestVO quest = row(55L, QuestStatus.AVAILABLE);
        given(questMapper.selectDetailByParent(55L, 1L)).willReturn(quest);
        given(s3Storage.presignedUrl("profile/2.png")).willReturn("https://signed/profile");

        QuestDetailResponseDTO result = service.getQuest(parent(), 55L);

        assertThat(result.getQuestId()).isEqualTo(55L);
        assertThat(result.getChild().getProfileImageUrl()).isEqualTo("https://signed/profile");
        assertThat(result.getLatestVerification()).isNull();
        verify(questMapper).selectDetailByParent(55L, 1L);
    }

    @Test
    void 접근할_수_없는_상세와_없는_상세는_같은_404다() {
        given(questMapper.selectDetailByChild(55L, 2L)).willReturn(null);

        assertError(
                () -> service.getQuest(child(), 55L),
                QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);
    }

    @Test
    void 최신_인증_이미지는_90일_전까지만_임시_URL을_제공한다() {
        QuestVO quest = row(55L, QuestStatus.PENDING);
        QuestVerificationVO verification = QuestVerificationVO.builder()
                .id(7L)
                .questId(55L)
                .attemptNo(2)
                .imageKey("quest-verifications/55/image.png")
                .content("인증 글")
                .status("PENDING")
                .createdAt(NOW.minusDays(89))
                .build();
        given(questMapper.selectDetailByParent(55L, 1L)).willReturn(quest);
        given(questMapper.selectLatestVerification(55L)).willReturn(verification);
        given(s3Storage.presignedUrl(verification.getImageKey())).willReturn("https://signed/image");

        QuestDetailResponseDTO result = service.getQuest(parent(), 55L);

        assertThat(result.getLatestVerification().getImageUrl()).isEqualTo("https://signed/image");
        assertThat(result.getLatestVerification().isImageExpired()).isFalse();
    }

    @Test
    void 업로드_90일이_되면_이미지를_노출하지_않는다() {
        QuestVO quest = row(55L, QuestStatus.PENDING);
        QuestVerificationVO verification = QuestVerificationVO.builder()
                .id(7L)
                .questId(55L)
                .attemptNo(2)
                .imageKey("quest-verifications/55/image.png")
                .status("PENDING")
                .createdAt(NOW.minusDays(90))
                .build();
        given(questMapper.selectDetailByParent(55L, 1L)).willReturn(quest);
        given(questMapper.selectLatestVerification(55L)).willReturn(verification);

        QuestDetailResponseDTO result = service.getQuest(parent(), 55L);

        assertThat(result.getLatestVerification().getImageUrl()).isNull();
        assertThat(result.getLatestVerification().isImageExpired()).isTrue();
        verify(s3Storage, never()).presignedUrl(verification.getImageKey());
    }

    @Test
    void 알_수_없는_역할은_조회하지_않는다() {
        assertError(
                () -> service.getQuests(
                        new MemberPrincipal(9L, "ADMIN"), QuestTab.AVAILABLE, null, null),
                CommonErrorCode.AUTH_FORBIDDEN);
    }

    private List<QuestVO> rows(int count, QuestStatus status) {
        List<QuestVO> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(row((long) i, status));
        }
        return rows;
    }

    private QuestVO row(Long id, QuestStatus status) {
        LocalDateTime time = NOW.plusDays(id);
        return QuestVO.builder()
                .id(id)
                .parentId(1L)
                .childId(2L)
                .childName("자녀")
                .childProfileImageKey("profile/2.png")
                .title("방 청소")
                .content("책상까지 정리")
                .deadline(time)
                .rewardAmount(1_000L)
                .teenyScoreEnabled(true)
                .verificationRequirement(VerificationRequirement.PHOTO_REQUIRED)
                .status(status)
                .remainingCount(3)
                .createdAt(NOW.minusDays(1))
                .endedAt(status == QuestStatus.COMPLETED ? time : null)
                .build();
    }

    private MemberPrincipal parent() {
        return new MemberPrincipal(1L, "PARENT");
    }

    private MemberPrincipal child() {
        return new MemberPrincipal(2L, "CHILD");
    }

    private void assertError(ThrowingCall call, Object expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
