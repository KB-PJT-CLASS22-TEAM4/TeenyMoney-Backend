package com.teenyfin.teenymoney.domain.quest.scheduler;

import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.service.QuestDeadlineService;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 스케줄러가 서비스를 어떤 주기로, 어떤 실패 태도로 부르는지 본다.
 *
 * 상한 자체(2,000건·20초)는 QuestDeadlineServiceTest 가 본다. 여기서는 상한에 걸려 남은
 * 물량이 다음 주기에 실제로 이어지는지를 두 번 호출로 확인한다. 서비스는 진짜를 쓰고
 * 매퍼만 밀린 대기열을 흉내 낸다.
 */
class QuestDeadlineSchedulerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-10T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 10, 0);

    private final QuestMapper questMapper = mock(QuestMapper.class);
    private final List<Long> closedQuestIds = new ArrayList<>();

    private QuestDeadlineService service;
    private QuestDeadlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        service = new QuestDeadlineService(
                questMapper,
                new TeenyScorePolicyService(),
                mock(TeenyScoreChangeService.class),
                mock(MemberMapper.class),
                mock(NotificationService.class),
                mock(PlatformTransactionManager.class),
                CLOCK);
        scheduler = new QuestDeadlineScheduler(service);
        given(questMapper.updateStatusForDeadline(any(), any(), any(), any(), any()))
                .willAnswer(invocation -> {
                    closedQuestIds.add(invocation.getArgument(0));
                    return 1;
                });
    }

    @Test
    @DisplayName("한 주기에 2,000건까지만 처리하고 남은 물량은 다음 주기가 이어서 끝낸다")
    void carriesTheRemainingBacklogOverToTheNextRun() {
        givenBacklog(2_500);

        scheduler.run();
        assertThat(closedQuestIds).hasSize(2_000);

        scheduler.run();
        assertThat(closedQuestIds).hasSize(2_500);

        // 이어 처리는 "빠짐없이 한 번씩"이어야 의미가 있다. 중복은 점수를 두 번 깎고,
        // 누락은 기한이 지난 퀘스트를 영원히 남긴다.
        assertThat(closedQuestIds).doesNotHaveDuplicates();
        assertThat(closedQuestIds).containsAll(idsFrom(1, 2_500));
    }

    @Test
    @DisplayName("대기열이 비면 다음 주기는 아무것도 처리하지 않는다")
    void doesNothingWhenNothingIsDue() {
        givenBacklog(0);

        scheduler.run();

        assertThat(closedQuestIds).isEmpty();
    }

    @Test
    @DisplayName("배치가 실패해도 예외를 밖으로 던지지 않는다")
    void swallowsFailureSoTheScheduleKeepsRunning() {
        // 던지면 Spring 이 이 @Scheduled 작업을 취소한다. 재배포 전까지 마감이 멈춘다.
        given(questMapper.selectDeadlineTargetsForUpdate(any(), any(), anyInt(), any()))
                .willThrow(new DataAccessResourceFailureException("DB 연결 끊김"));

        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("1분 주기로 실행한다")
    void runsEveryMinute() throws NoSuchMethodException {
        Scheduled scheduled = QuestDeadlineScheduler.class
                .getMethod("run").getAnnotation(Scheduled.class);

        // fixedRate 가 아니라 fixedDelay 다. 한 주기가 20초까지 걸릴 수 있어서,
        // 이전 실행이 끝난 뒤부터 1분을 세야 실행이 겹치지 않는다.
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelay()).isEqualTo(60_000L);
    }

    /** AVAILABLE 대기열만 밀려 있다고 본다. 조회할 때마다 요청한 만큼 앞에서 꺼내 준다. */
    private void givenBacklog(int size) {
        Deque<Long> pending = new ArrayDeque<>(idsFrom(1, size));
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.AVAILABLE), eq(NOW), anyInt(), any()))
                .willAnswer(invocation -> {
                    int limit = invocation.getArgument(2);
                    List<QuestVO> batch = new ArrayList<>(limit);
                    while (batch.size() < limit && !pending.isEmpty()) {
                        batch.add(QuestVO.builder()
                                .id(pending.poll())
                                .status(QuestStatus.AVAILABLE)
                                .deadline(NOW.minusDays(1))
                                .teenyScoreEnabled(false)
                                .build());
                    }
                    return batch;
                });
        given(questMapper.selectDeadlineTargetsForUpdate(
                eq(QuestStatus.IN_PROGRESS), eq(NOW), anyInt(), any()))
                .willReturn(List.of());
    }

    private List<Long> idsFrom(long first, long last) {
        List<Long> ids = new ArrayList<>();
        for (long id = first; id <= last; id++) {
            ids.add(id);
        }
        return ids;
    }
}
