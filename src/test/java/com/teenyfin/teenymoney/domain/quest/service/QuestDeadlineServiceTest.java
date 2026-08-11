package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
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
    private QuestDeadlineService service;

    @BeforeEach
    void setUp() {
        service = new QuestDeadlineService(
                questMapper, mock(PlatformTransactionManager.class), CLOCK);
        // 잠근 행은 전부 갱신에 성공한다고 본다. 실패 경로는 별도 테스트에서 다룬다.
        given(questMapper.updateStatusForDeadline(any(), any(), any(), any())).willReturn(1);
    }

    @Test
    @DisplayName("기한이 지난 AVAILABLE 퀘스트를 EXPIRED로 바꾼다")
    void movesOverdueAvailableQuestsToExpired() {
        givenTargets(3, 0);

        int processed = service.closeExpired();

        assertThat(processed).isEqualTo(3);
        verify(questMapper).updateStatusForDeadline(
                1L, QuestStatus.AVAILABLE, QuestStatus.EXPIRED, NOW);
        verify(questMapper).updateStatusForDeadline(
                3L, QuestStatus.AVAILABLE, QuestStatus.EXPIRED, NOW);
    }

    @Test
    @DisplayName("IN_PROGRESS와 PENDING은 이번 단계에서 건드리지 않는다")
    void doesNotTouchInProgressOrPendingYet() {
        givenTargets(1, 0);

        service.closeExpired();

        // IN_PROGRESS -> FAILED 는 티니점수 -2 배관이 준비된 뒤에 붙인다.
        // PENDING 은 부모가 검토할 수 있어야 하므로 애초에 자동 종료 대상이 아니다(설계 15.4).
        verify(questMapper, never()).selectDeadlineTargetsForUpdate(
                eq(QuestStatus.IN_PROGRESS), any(), anyInt());
        verify(questMapper, never()).selectDeadlineTargetsForUpdate(
                eq(QuestStatus.PENDING), any(), anyInt());
    }

    @Test
    @DisplayName("대상이 없으면 한 번만 조회하고 끝낸다")
    void stopsAfterOneQueryWhenNothingIsDue() {
        givenTargets(0);

        int processed = service.closeExpired();

        assertThat(processed).isZero();
        verify(questMapper, times(1)).selectDeadlineTargetsForUpdate(any(), any(), anyInt());
        verify(questMapper, never()).updateStatusForDeadline(any(), any(), any(), any());
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
        given(questMapper.selectDeadlineTargetsForUpdate(any(), any(), anyInt()))
                .willAnswer(invocation -> quests(invocation.getArgument(2)));

        int processed = service.closeExpired();

        assertThat(processed).isEqualTo(2_000);
        // 마지막 묶음은 남은 만큼만 요청해야 2000 을 정확히 지킨다.
        assertThat(capturedLimits().stream().mapToInt(Integer::intValue).sum()).isEqualTo(2_000);
    }

    @Test
    @DisplayName("갱신이 0건이면 대상 소진으로 보고 멈춘다")
    void stopsWhenUpdateChangesNothing() {
        givenTargets(200, 200);
        given(questMapper.updateStatusForDeadline(any(), any(), any(), any())).willReturn(0);

        int processed = service.closeExpired();

        assertThat(processed).isZero();
        // 갱신이 안 되는데 계속 조회하면 무한 루프가 된다.
        verify(questMapper, times(1)).selectDeadlineTargetsForUpdate(any(), any(), anyInt());
    }

    @Test
    @DisplayName("마감 기준 시각은 실행 중에 바뀌지 않는다")
    void usesSameCutoffTimeForEveryBatch() {
        givenTargets(200, 200, 0);

        service.closeExpired();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(questMapper, times(3))
                .selectDeadlineTargetsForUpdate(any(), captor.capture(), anyInt());
        // 묶음마다 now 를 새로 구하면 조회 기준이 흔들린다. 한 차례 실행은 한 시점 기준이다.
        assertThat(captor.getAllValues()).containsOnly(NOW);
    }

    @Test
    @DisplayName("종료 시각은 초 단위로 잘라 기록한다")
    void recordsEndedAtTruncatedToSeconds() {
        givenTargets(1, 0);

        service.closeExpired();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(questMapper).updateStatusForDeadline(any(), any(), any(), captor.capture());
        // 밀리초가 남으면 기한과 같은 초인지 비교가 어긋난다(설계 15.4).
        assertThat(captor.getValue().getNano()).isZero();
        assertThat(captor.getValue()).isEqualTo(NOW);
    }

    // ---------- 도우미 ----------

    /** 호출 순서대로 이만큼씩 돌려준다. 마지막 값이 0이면 거기서 대상이 소진된 것이다. */
    private void givenTargets(int... counts) {
        var stub = given(questMapper.selectDeadlineTargetsForUpdate(any(), any(), anyInt()));
        for (int count : counts) {
            stub = stub.willReturn(quests(count));
        }
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

    private List<Integer> capturedLimits() {
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(questMapper, org.mockito.Mockito.atLeastOnce())
                .selectDeadlineTargetsForUpdate(any(), any(), captor.capture());
        return captor.getAllValues();
    }
}
