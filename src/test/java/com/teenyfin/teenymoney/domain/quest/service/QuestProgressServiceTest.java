package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.dto.request.QuestDeclineRequestDTO;
import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.DeclineReasonCode;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVerificationVO;
import com.teenyfin.teenymoney.domain.quest.vo.VerificationRequirement;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.exception.ErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import com.teenyfin.teenymoney.global.storage.S3Storage;
import com.teenyfin.teenymoney.global.storage.StorageErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
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
    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02};

    private final QuestMapper questMapper = mock(QuestMapper.class);
    private QuestProgressService service;

    private final S3Storage s3Storage = mock(S3Storage.class);

    @BeforeEach
    void setUp() {
        service = new QuestProgressService(
                questMapper,
                new QuestStatePolicy(),
                s3Storage,
                mock(PlatformTransactionManager.class),
                CLOCK);
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

    // ---------- 인증 제출: 상태와 시도 번호 ----------

    @Test
    @DisplayName("첫 인증은 시도 1번으로 저장되고 퀘스트를 PENDING으로 바꾼다")
    void firstSubmissionIsAttemptOneAndMovesQuestToPending() {
        lock(quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1), VerificationRequirement.TEXT_REQUIRED, 3));
        given(questMapper.selectLatestVerification(QUEST_ID)).willReturn(null);
        given(questMapper.insertVerification(any())).willReturn(1);
        given(questMapper.updateStatusByChild(any(), any(), any(), any(), any())).willReturn(1);

        service.submitVerification(child(), QUEST_ID, "  다 했어요  ", null);

        QuestVerificationVO saved = capturedVerification();
        assertThat(saved.getAttemptNo()).isEqualTo(1);
        assertThat(saved.getContent()).isEqualTo("다 했어요");
        assertThat(saved.getImageKey()).isNull();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        verify(questMapper).updateStatusByChild(
                QUEST_ID, CHILD_ID, QuestStatus.IN_PROGRESS, QuestStatus.PENDING, NOW);
    }

    @Test
    @DisplayName("재제출은 이전 시도를 덮어쓰지 않고 다음 번호를 쓴다")
    void resubmissionUsesNextAttemptNumberWithoutOverwriting() {
        lock(quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1), VerificationRequirement.TEXT_REQUIRED, 2));
        given(questMapper.selectLatestVerification(QUEST_ID))
                .willReturn(QuestVerificationVO.builder().id(9L).attemptNo(1).build());
        given(questMapper.insertVerification(any())).willReturn(1);
        given(questMapper.updateStatusByChild(any(), any(), any(), any(), any())).willReturn(1);

        service.submitVerification(child(), QUEST_ID, "다시 했어요", null);

        assertThat(capturedVerification().getAttemptNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("수락하지 않았거나 이미 제출한 퀘스트에는 인증할 수 없다")
    void cannotSubmitWhenQuestIsNotInProgress() {
        lock(quest(QuestStatus.AVAILABLE, NOW.plusDays(1), VerificationRequirement.FREE, 3));
        assertError(() -> service.submitVerification(child(), QUEST_ID, "했어요", null),
                QuestErrorCode.QUEST_STATUS_CONFLICT);

        // 첫 제출이 PENDING 으로 바꾸므로, 중복 제출도 같은 경로로 막힌다(불변식 7).
        lock(quest(QuestStatus.PENDING, NOW.plusDays(1), VerificationRequirement.FREE, 3));
        assertError(() -> service.submitVerification(child(), QUEST_ID, "했어요", null),
                QuestErrorCode.QUEST_STATUS_CONFLICT);

        verify(questMapper, never()).insertVerification(any());
    }

    @Test
    @DisplayName("기한이 지나면 인증을 제출할 수 없다")
    void cannotSubmitAfterDeadline() {
        lock(quest(QuestStatus.IN_PROGRESS, NOW.minusSeconds(1), VerificationRequirement.FREE, 3));

        assertError(() -> service.submitVerification(child(), QUEST_ID, "했어요", null),
                QuestErrorCode.QUEST_DEADLINE_PASSED);
        verify(questMapper, never()).insertVerification(any());
    }

    @Test
    @DisplayName("남은 기회가 없으면 제출할 수 없다")
    void cannotSubmitWhenNoAttemptsRemain() {
        lock(quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1), VerificationRequirement.FREE, 0));

        assertError(() -> service.submitVerification(child(), QUEST_ID, "했어요", null),
                QuestErrorCode.QUEST_VERIFICATION_ATTEMPT_EXCEEDED);
        verify(questMapper, never()).insertVerification(any());
    }

    @Test
    @DisplayName("부모는 인증을 제출할 수 없다")
    void parentCannotSubmitVerification() {
        assertError(() -> service.submitVerification(
                        new MemberPrincipal(1L, "PARENT"), QUEST_ID, "했어요", null),
                QuestErrorCode.QUEST_CHILD_ONLY);
        verify(questMapper, never()).selectByIdForUpdateByChild(any(), any());
    }

    // ---------- 인증 제출: 인증 방식 ----------

    @Test
    @DisplayName("FREE는 사진도 글도 없이 제출할 수 있다")
    void freeAcceptsEmptySubmission() {
        lock(quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1), VerificationRequirement.FREE, 3));
        given(questMapper.insertVerification(any())).willReturn(1);
        given(questMapper.updateStatusByChild(any(), any(), any(), any(), any())).willReturn(1);

        service.submitVerification(child(), QUEST_ID, "   ", null);

        // 공백만 있는 글은 없는 값이다. 둘 다 비어도 FREE 면 제출 자체가 인증이다(§5).
        assertThat(capturedVerification().getContent()).isNull();
    }

    @Test
    @DisplayName("TEXT_REQUIRED는 공백만 있는 글을 빈 값으로 보고 거부한다")
    void textRequiredRejectsBlankOnlyContent() {
        lock(quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1), VerificationRequirement.TEXT_REQUIRED, 3));

        assertError(() -> service.submitVerification(child(), QUEST_ID, "   ", null),
                QuestErrorCode.QUEST_VERIFICATION_REQUIREMENT_UNMET);
    }

    @Test
    @DisplayName("PHOTO_REQUIRED는 글만으로 제출할 수 없고 업로드도 하지 않는다")
    void photoRequiredRejectsTextOnlyWithoutUploading() {
        lock(quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1), VerificationRequirement.PHOTO_REQUIRED, 3));

        assertError(() -> service.submitVerification(child(), QUEST_ID, "글만 썼어요", null),
                QuestErrorCode.QUEST_VERIFICATION_REQUIREMENT_UNMET);
        verify(s3Storage, never()).upload(anyString(), any());
    }

    @Test
    @DisplayName("ANY_REQUIRED는 글만 있어도 통과하고 둘 다 없으면 거부한다")
    void anyRequiredNeedsAtLeastOne() {
        lock(quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1), VerificationRequirement.ANY_REQUIRED, 3));
        given(questMapper.insertVerification(any())).willReturn(1);
        given(questMapper.updateStatusByChild(any(), any(), any(), any(), any())).willReturn(1);

        assertThatCode(() -> service.submitVerification(child(), QUEST_ID, "했어요", null))
                .doesNotThrowAnyException();
        assertError(() -> service.submitVerification(child(), QUEST_ID, null, null),
                QuestErrorCode.QUEST_VERIFICATION_REQUIREMENT_UNMET);
    }

    @Test
    @DisplayName("인증 글이 500자를 넘으면 조회 전에 400이다")
    void contentOver500CharactersIsRejectedBeforeQuery() {
        assertError(() -> service.submitVerification(child(), QUEST_ID, "가".repeat(501), null),
                CommonErrorCode.COMMON_INVALID_INPUT);
        verify(questMapper, never()).selectByIdForUpdateByChild(any(), any());
    }

    // ---------- 인증 제출: 이미지와 트랜잭션 경계 ----------

    @Test
    @DisplayName("사진은 퀘스트별 접두사로 올리고 DB에는 키만 저장한다")
    void imageIsUploadedUnderQuestPrefixAndOnlyKeyIsStored() {
        QuestVO quest = quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1),
                VerificationRequirement.PHOTO_REQUIRED, 3);
        lock(quest);
        given(questMapper.selectDetailByChild(QUEST_ID, CHILD_ID)).willReturn(quest);
        given(s3Storage.upload(eq("quest-verifications/55"), any()))
                .willReturn("quest-verifications/55/abc.png");
        given(questMapper.insertVerification(any())).willReturn(1);
        given(questMapper.updateStatusByChild(any(), any(), any(), any(), any())).willReturn(1);

        service.submitVerification(child(), QUEST_ID, null, png("proof.png"));

        // presigned URL 이 아니라 키를 저장한다(불변식 16).
        assertThat(capturedVerification().getImageKey()).isEqualTo("quest-verifications/55/abc.png");
    }

    @Test
    @DisplayName("업로드는 인증 이력 삽입보다 먼저 끝난다")
    void uploadHappensBeforeInsert() {
        QuestVO quest = quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1),
                VerificationRequirement.PHOTO_REQUIRED, 3);
        lock(quest);
        given(questMapper.selectDetailByChild(QUEST_ID, CHILD_ID)).willReturn(quest);
        given(s3Storage.upload(anyString(), any())).willReturn("key.png");
        given(questMapper.insertVerification(any())).willReturn(1);
        given(questMapper.updateStatusByChild(any(), any(), any(), any(), any())).willReturn(1);

        service.submitVerification(child(), QUEST_ID, null, png("proof.png"));

        // S3 업로드가 트랜잭션 밖에 있어야 커넥션을 붙잡지 않는다(설계 15.3).
        InOrder order = inOrder(s3Storage, questMapper);
        order.verify(s3Storage).upload(anyString(), any());
        order.verify(questMapper).insertVerification(any());
    }

    @Test
    @DisplayName("상태가 틀리면 사진을 올리기 전에 막아 고아 객체를 만들지 않는다")
    void wrongStateIsRejectedBeforeUploadSoNoOrphanObject() {
        QuestVO pending = quest(QuestStatus.PENDING, NOW.plusDays(1),
                VerificationRequirement.PHOTO_REQUIRED, 3);
        given(questMapper.selectDetailByChild(QUEST_ID, CHILD_ID)).willReturn(pending);

        assertError(() -> service.submitVerification(child(), QUEST_ID, null, png("proof.png")),
                QuestErrorCode.QUEST_STATUS_CONFLICT);

        // 올린 뒤 롤백되면 지울 권한이 없어 90일간 남는다. 올리기 전에 막는 이유다.
        verify(s3Storage, never()).upload(anyString(), any());
        verify(questMapper, never()).insertVerification(any());
    }

    @Test
    @DisplayName("확장자와 실제 바이트가 어긋난 사진은 올리지 않는다")
    void imageWithMismatchedMagicBytesIsRejected() {
        MultipartFile fake = new MockMultipartFile(
                "image", "proof.png", "image/png", "not a png".getBytes(StandardCharsets.UTF_8));

        assertError(() -> service.submitVerification(child(), QUEST_ID, null, fake),
                StorageErrorCode.STORAGE_UNSUPPORTED_TYPE);
        verify(s3Storage, never()).upload(anyString(), any());
    }

    @Test
    @DisplayName("허용하지 않는 확장자는 거부한다")
    void unsupportedExtensionIsRejected() {
        MultipartFile gif = new MockMultipartFile("image", "proof.gif", "image/gif", PNG);

        assertError(() -> service.submitVerification(child(), QUEST_ID, null, gif),
                StorageErrorCode.STORAGE_UNSUPPORTED_TYPE);
        verify(s3Storage, never()).upload(anyString(), any());
    }

    @Test
    @DisplayName("빈 파일 파트는 사진이 없는 것으로 본다")
    void emptyFilePartCountsAsNoImage() {
        lock(quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1), VerificationRequirement.TEXT_REQUIRED, 3));
        given(questMapper.insertVerification(any())).willReturn(1);
        given(questMapper.updateStatusByChild(any(), any(), any(), any(), any())).willReturn(1);
        MultipartFile empty = new MockMultipartFile("image", "", "image/png", new byte[0]);

        service.submitVerification(child(), QUEST_ID, "글로만 인증", empty);

        assertThat(capturedVerification().getImageKey()).isNull();
        verify(s3Storage, never()).upload(anyString(), any());
    }

    private MemberPrincipal child() {
        return new MemberPrincipal(CHILD_ID, "CHILD");
    }

    private MultipartFile png(String filename) {
        return new MockMultipartFile("image", filename, "image/png", PNG);
    }

    private QuestVerificationVO capturedVerification() {
        ArgumentCaptor<QuestVerificationVO> captor =
                ArgumentCaptor.forClass(QuestVerificationVO.class);
        verify(questMapper).insertVerification(captor.capture());
        return captor.getValue();
    }

    private QuestVO quest(QuestStatus status, LocalDateTime deadline,
                          VerificationRequirement requirement, int remaining) {
        QuestVO quest = quest(status, deadline);
        quest.setVerificationRequirement(requirement);
        quest.setRemainingCount(remaining);
        return quest;
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
