package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.teenyscore.dto.request.TeenyScoreChangeRequestDTO;
import com.teenyfin.teenymoney.domain.teenyscore.exception.TeenyScoreErrorCode;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreEventCode;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class QuestDeadlineServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // NOW = 2026-08-10T10:00 (KST). 마감 경계를 초 단위로 고정하려면 시각이 고정돼야 한다.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-10T01:00:00Z"), SEOUL);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 10, 0);

    private final QuestMapper questMapper = mock(QuestMapper.class);
    private final TeenyScoreChangeService teenyScoreChangeService =
            mock(TeenyScoreChangeService.class);
    private QuestDeadlineService service;

    @BeforeEach
    void setUp() {
        service = new QuestDeadlineService(
                questMapper,
                new TeenyScorePolicyService(),
                teenyScoreChangeService,
                mock(PlatformTransactionManager.class),
                CLOCK);
        // 잠근 행은 전부 갱신에 성공한다고 본다. 실패 경로는 별도 테스트에서 다룬다.
        given(questMapper.updateStatusForDeadline(any(), any(), any(), any(), any())).willReturn(1);
    }

    @Test
    @DisplayName("기한이 지난 AVAILABLE 퀘스트를 EXPIRED로 바꾼다")
    void movesOverdueAvailableQuestsToExpired() {
        givenTargets(3, 0);

        int processed = service.closeExpired();

        assertThat(processed).isEqualTo(3);
        verify(questMapper).updateStatusForDeadline(
                1L, QuestStatus.AVAILABLE, QuestStatus.EXPIRED, null, NOW);
        verify(questMapper).updateStatusForDeadline(
                3L, QuestStatus.AVAILABLE, QuestStatus.EXPIRED, null, NOW);
    }

    @Test
    @DisplayName("기한이 지난 IN_PROGRESS는 FAILED로 바꾸고 PENDING은 조회하지 않는다")
    void movesOverdueInProgressToFailedButLeavesPendingAlone() {
        givenNoAvailableTargets();
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.IN_PROGRESS), eq(NOW), eq(200), any()))
                .willReturn(List.of(QuestVO.builder()
                        .id(20L)
                        .status(QuestStatus.IN_PROGRESS)
                        .deadline(NOW.minusDays(1))
                        .build()))
                .willReturn(List.of());

        int processed = service.closeExpired();

        assertThat(processed).isEqualTo(1);
        verify(questMapper).updateStatusForDeadline(
                20L, QuestStatus.IN_PROGRESS, QuestStatus.FAILED, 0, NOW);
        verify(questMapper, never()).selectDeadlineTargetsForUpdate(
                eq(QuestStatus.PENDING), any(), anyInt(), any());
    }

    @Test
    @DisplayName("점수 대상 IN_PROGRESS가 만료되면 QUEST_FAILED -2를 한 번 적용한다")
    void appliesQuestFailureScoreWhenScoredInProgressQuestExpires() {
        givenNoAvailableTargets();
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.IN_PROGRESS), eq(NOW), eq(200), any()))
                .willReturn(List.of(QuestVO.builder()
                        .id(20L)
                        .childId(2L)
                        .teenyScoreEnabled(true)
                        .status(QuestStatus.IN_PROGRESS)
                        .deadline(NOW.minusDays(1))
                        .build()))
                .willReturn(List.of());

        service.closeExpired();

        ArgumentCaptor<TeenyScoreChangeRequestDTO> captor =
                ArgumentCaptor.forClass(TeenyScoreChangeRequestDTO.class);
        verify(teenyScoreChangeService).change(captor.capture());
        TeenyScoreChangeRequestDTO request = captor.getValue();
        assertThat(request.getChildId()).isEqualTo(2L);
        assertThat(request.getEventCode()).isEqualTo(TeenyScoreEventCode.QUEST_FAILED);
        assertThat(request.getAmount()).isEqualTo(-2);
        assertThat(request.getEventKey()).isEqualTo("QUEST_FAILED:20");
        assertThat(request.getReferenceType()).isEqualTo("QUEST");
        assertThat(request.getReferenceId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("점수 대상 자녀가 아니면 점수만 건너뛰고 퀘스트는 마감한다")
    void closesQuestEvenWhenChildCannotBeScored() {
        givenNoAvailableTargets();
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.IN_PROGRESS), eq(NOW), eq(200), any()))
                .willReturn(List.of(scoredInProgressQuest(20L)))
                .willReturn(List.of());
        given(teenyScoreChangeService.change(any()))
                .willThrow(new BusinessException(
                        TeenyScoreErrorCode.TEENY_SCORE_CHILD_NOT_FOUND));

        int processed = service.closeExpired();

        // 점수를 못 준다고 마감까지 되돌리면 기한 지난 퀘스트가 매 실행 다시 올라온다.
        assertThat(processed).isEqualTo(1);
        verify(questMapper).updateStatusForDeadline(
                20L, QuestStatus.IN_PROGRESS, QuestStatus.FAILED, 0, NOW);
    }

    @Test
    @DisplayName("한 퀘스트의 점수 처리 실패가 같은 묶음의 다음 퀘스트를 취소하지 않는다")
    void continuesBatchAfterOneQuestBusinessFailure() {
        givenNoAvailableTargets();
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.IN_PROGRESS), eq(NOW), eq(200), any()))
                .willReturn(List.of(
                        scoredInProgressQuest(20L),
                        scoredInProgressQuest(21L)))
                .willReturn(List.of());
        given(teenyScoreChangeService.change(any()))
                .willThrow(new BusinessException(
                        TeenyScoreErrorCode.TEENY_SCORE_GRADE_NOT_FOUND))
                .willReturn(null);

        int processed = service.closeExpired();

        assertThat(processed).isEqualTo(1);
        verify(questMapper).updateStatusForDeadline(
                21L, QuestStatus.IN_PROGRESS, QuestStatus.FAILED, 0, NOW);
        verify(teenyScoreChangeService, times(2)).change(any());
    }

    @Test
    @DisplayName("한 퀘스트의 데이터 제약 오류가 같은 묶음의 다음 퀘스트를 취소하지 않는다")
    void continuesBatchAfterOneQuestDataIntegrityFailure() {
        givenNoAvailableTargets();
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.IN_PROGRESS), eq(NOW), eq(200), any()))
                .willReturn(List.of(
                        scoredInProgressQuest(20L),
                        scoredInProgressQuest(21L)))
                .willReturn(List.of());
        given(teenyScoreChangeService.change(any()))
                .willThrow(new DataIntegrityViolationException("invalid quest score row"))
                .willReturn(null);

        int processed = service.closeExpired();

        assertThat(processed).isEqualTo(1);
        verify(teenyScoreChangeService, times(2)).change(any());
    }

    @Test
    @DisplayName("DB 연결 장애는 묶음 전체를 롤백하도록 호출자에게 전파한다")
    void propagatesDatabaseResourceFailure() {
        givenNoAvailableTargets();
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.IN_PROGRESS), eq(NOW), eq(200), any()))
                .willReturn(List.of(
                        scoredInProgressQuest(20L),
                        scoredInProgressQuest(21L)));
        given(teenyScoreChangeService.change(any()))
                .willThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(service::closeExpired)
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(questMapper, never()).updateStatusForDeadline(
                21L, QuestStatus.IN_PROGRESS, QuestStatus.FAILED, 0, NOW);
    }

    @Test
    @DisplayName("실패한 퀘스트는 같은 실행의 다음 조회에서 제외 목록으로 넘어간다")
    void passesFailedQuestIdsToTheNextQuery() {
        givenNoAvailableTargets();
        List<Set<Long>> excludedPerCall = new ArrayList<>();
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.IN_PROGRESS), eq(NOW), eq(200), any()))
                .willAnswer(invocation -> {
                    excludedPerCall.add(Set.copyOf(invocation.getArgument(3)));
                    return excludedPerCall.size() == 1
                            ? List.of(scoredInProgressQuest(20L), scoredInProgressQuest(21L))
                            : List.of();
                });
        given(teenyScoreChangeService.change(any()))
                .willAnswer(invocation -> {
                    TeenyScoreChangeRequestDTO request = invocation.getArgument(0);
                    if (request.getReferenceId().equals(20L)) {
                        throw new BusinessException(
                                TeenyScoreErrorCode.TEENY_SCORE_GRADE_NOT_FOUND);
                    }
                    return null;
                });

        int processed = service.closeExpired();

        assertThat(processed).isEqualTo(1);
        // 자바에서만 건너뛰면 실패 행이 계속 (deadline, id) 정렬 맨 앞을 차지해
        // 조회 창이 앞으로 나가지 못하고 뒤의 정상 대상이 막힌다.
        assertThat(excludedPerCall.get(0)).isEmpty();
        assertThat(excludedPerCall.get(1)).containsExactly(20L);
    }

    @Test
    @DisplayName("대상이 없으면 AVAILABLE과 IN_PROGRESS를 한 번씩 확인하고 끝낸다")
    void checksEachDeadlineStatusOnceWhenNothingIsDue() {
        givenTargets(0);

        int processed = service.closeExpired();

        assertThat(processed).isZero();
        verify(questMapper, times(2))
                .selectDeadlineTargetsForUpdate(any(), any(), anyInt(), any());
        verify(questMapper, never()).updateStatusForDeadline(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("한 번에 200건씩 나눠 잠근다")
    void locksAtMost200RowsPerQuery() {
        givenTargets(200, 200, 50, 0);

        int processed = service.closeExpired();

        assertThat(processed).isEqualTo(450);
        // 200 을 넘겨 잠그면 그 트랜잭션이 끝날 때까지 그만큼의 행이 묶인다.
        assertThat(capturedLimits()).allSatisfy(limit -> assertThat(limit).isLessThanOrEqualTo(200));
    }

    @Test
    @DisplayName("한 차례 실행이 2000건을 넘지 않는다")
    void neverProcessesMoreThan2000PerRun() {
        // 항상 요청한 만큼 돌려준다. 상한이 없으면 무한히 처리한다.
        given(questMapper.selectDeadlineTargetsForUpdate(any(), any(), anyInt(), any()))
                .willAnswer(invocation -> quests(invocation.getArgument(2)));

        int processed = service.closeExpired();

        assertThat(processed).isEqualTo(2_000);
        // 마지막 묶음은 남은 만큼만 요청해야 2000 을 정확히 지킨다.
        assertThat(capturedLimits().stream().mapToInt(Integer::intValue).sum()).isEqualTo(2_000);
    }

    @Test
    @DisplayName("AVAILABLE이 2000건 넘게 밀려도 IN_PROGRESS를 같은 실행에서 처리한다")
    void doesNotStarveInProgressWhenAvailableBacklogIsLarge() {
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.AVAILABLE), any(), anyInt(), any()))
                .willAnswer(invocation -> quests(invocation.getArgument(2)));
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.IN_PROGRESS), any(), anyInt(), any()))
                .willReturn(List.of(QuestVO.builder()
                        .id(3_000L)
                        .status(QuestStatus.IN_PROGRESS)
                        .deadline(NOW.minusDays(1))
                        .build()))
                .willReturn(List.of());

        service.closeExpired();

        verify(questMapper).updateStatusForDeadline(
                3_000L, QuestStatus.IN_PROGRESS, QuestStatus.FAILED, 0, NOW);
    }

    @Test
    @DisplayName("상태 충돌로 갱신되지 않은 퀘스트를 같은 실행에서 다시 갱신하지 않는다")
    void doesNotRetryQuestWhenDeadlineUpdateChangesNothing() {
        List<Set<Long>> excludedPerCall = new ArrayList<>();
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.AVAILABLE), eq(NOW), eq(200), any()))
                .willAnswer(invocation -> {
                    excludedPerCall.add(Set.copyOf(invocation.getArgument(3)));
                    return excludedPerCall.size() == 1 ? quests(1) : List.of();
                });
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.IN_PROGRESS), eq(NOW), eq(200), any())).willReturn(List.of());
        given(questMapper.updateStatusForDeadline(any(), any(), any(), any(), any())).willReturn(0);

        int processed = service.closeExpired();

        assertThat(processed).isZero();
        verify(questMapper, times(1)).updateStatusForDeadline(
                1L, QuestStatus.AVAILABLE, QuestStatus.EXPIRED, null, NOW);
        assertThat(excludedPerCall.get(1)).containsExactly(1L);
    }

    @Test
    @DisplayName("마감 기준 시각은 실행 중에 바뀌지 않는다")
    void usesSameCutoffTimeForEveryBatch() {
        givenTargets(200, 200, 0);

        service.closeExpired();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(questMapper, times(4))
                .selectDeadlineTargetsForUpdate(any(), captor.capture(), anyInt(), any());
        // 묶음마다 now 를 새로 구하면 조회 기준이 흔들린다. 한 차례 실행은 한 시점 기준이다.
        assertThat(captor.getAllValues()).containsOnly(NOW);
    }

    @Test
    @DisplayName("종료 시각은 초 단위로 잘라 기록한다")
    void recordsEndedAtTruncatedToSeconds() {
        givenTargets(1, 0);

        service.closeExpired();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(questMapper).updateStatusForDeadline(any(), any(), any(), any(), captor.capture());
        // 밀리초가 남으면 기한과 같은 초인지 비교가 어긋난다(설계 15.4).
        assertThat(captor.getValue().getNano()).isZero();
        assertThat(captor.getValue()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("nanoTime이 최댓값을 넘어도 실행 시간을 올바르게 비교한다")
    void runWindowComparisonSurvivesNanoTimeOverflow() {
        long startedAt = Long.MAX_VALUE - 5;
        long tenNanosLater = Long.MIN_VALUE + 4;

        assertThat(QuestDeadlineService.withinRunWindow(startedAt, tenNanosLater))
                .isTrue();
        assertThat(QuestDeadlineService.withinRunWindow(
                0L, Duration.ofSeconds(20).toNanos()))
                .isFalse();
    }

    // ---------- 도우미 ----------

    /** 호출 순서대로 이만큼씩 돌려준다. 마지막 값이 0이면 거기서 대상이 소진된 것이다. */
    private void givenTargets(int... counts) {
        var stub = given(questMapper.selectDeadlineTargetsForUpdate(
                any(), any(), anyInt(), any()));
        for (int count : counts) {
            stub = stub.willReturn(quests(count));
        }
    }

    private void givenNoAvailableTargets() {
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.AVAILABLE), eq(NOW), eq(200), any())).willReturn(List.of());
    }

    private List<QuestVO> quests(int count) {
        List<QuestVO> list = new ArrayList<>(count);
        IntStream.rangeClosed(1, count).forEach(i ->
                list.add(QuestVO.builder()
                        .id((long) i)
                        .status(QuestStatus.AVAILABLE)
                        .deadline(NOW.minusDays(1))
                        .build()));
        return list;
    }

    private QuestVO scoredInProgressQuest(long id) {
        return QuestVO.builder()
                .id(id)
                .childId(2L)
                .teenyScoreEnabled(true)
                .status(QuestStatus.IN_PROGRESS)
                .deadline(NOW.minusDays(1))
                .build();
    }

    private List<Integer> capturedLimits() {
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(questMapper, org.mockito.Mockito.atLeastOnce())
                .selectDeadlineTargetsForUpdate(any(), any(), captor.capture(), any());
        return captor.getAllValues();
    }
}
