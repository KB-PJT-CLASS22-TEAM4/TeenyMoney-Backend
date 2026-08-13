package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.teenyscore.exception.TeenyScoreErrorCode;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class QuestDeadlineService {

    private static final Logger log = LoggerFactory.getLogger(QuestDeadlineService.class);
    // 이슈가 정한 값. 규모가 커지면 여기 숫자만 바꾼다.
    private static final int LOCK_BATCH_SIZE = 200;
    private static final int MAX_PER_RUN = 2_000;
    private static final Duration MAX_RUN_DURATION = Duration.ofSeconds(20);

    private final QuestMapper questMapper;
    private final TeenyScorePolicyService teenyScorePolicyService;
    private final TeenyScoreChangeService teenyScoreChangeService;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate nestedTransactionTemplate;
    private final Clock clock;

    public QuestDeadlineService(QuestMapper questMapper,
                                TeenyScorePolicyService teenyScorePolicyService,
                                TeenyScoreChangeService teenyScoreChangeService,
                                PlatformTransactionManager transactionManager,
                                Clock clock) {
        this.questMapper = questMapper;
        this.teenyScorePolicyService = teenyScorePolicyService;
        this.teenyScoreChangeService = teenyScoreChangeService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.nestedTransactionTemplate = new TransactionTemplate(transactionManager);
        this.nestedTransactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_NESTED);
        this.clock = clock;
    }

    /** 한 차례 실행. 처리한 건수를 반환한다. */
    public int closeExpired() {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        long startedAt = System.nanoTime();
        Set<Long> failedQuestIds = new HashSet<>();
        boolean availableExhausted = false;
        boolean inProgressExhausted = false;
        int attempted = 0;
        int processed = 0;

        try {
            while (attempted < MAX_PER_RUN
                    && withinRunWindow(startedAt)
                    && (!availableExhausted || !inProgressExhausted)) {
                if (!availableExhausted) {
                    BatchResult result = closeBatchInTransaction(
                            QuestStatus.AVAILABLE,
                            QuestStatus.EXPIRED,
                            now,
                            Math.min(LOCK_BATCH_SIZE, MAX_PER_RUN - attempted),
                            failedQuestIds);
                    attempted += result.attempted();
                    processed += result.changed();
                    availableExhausted = result.attempted() == 0;
                }
                if (!inProgressExhausted
                        && attempted < MAX_PER_RUN
                        && withinRunWindow(startedAt)) {
                    BatchResult result = closeBatchInTransaction(
                            QuestStatus.IN_PROGRESS,
                            QuestStatus.FAILED,
                            now,
                            Math.min(LOCK_BATCH_SIZE, MAX_PER_RUN - attempted),
                            failedQuestIds);
                    attempted += result.attempted();
                    processed += result.changed();
                    inProgressExhausted = result.attempted() == 0;
                }
            }
        } catch (RuntimeException e) {
            // 이미 커밋된 묶음의 건수가 예외와 함께 사라진다. 숫자만 남기고 스택은 스케줄러가 찍는다.
            log.warn("퀘스트 마감 중단. 중단 전까지 {}건 처리", processed);
            throw e;
        }
        return processed;
    }

    /** nanoTime 의 원점은 임의값이라 더하면 넘칠 수 있다. 시작 시각과의 차이로 본다. */
    private boolean withinRunWindow(long startedAt) {
        return withinRunWindow(startedAt, System.nanoTime());
    }

    static boolean withinRunWindow(long startedAt, long currentTime) {
        return currentTime - startedAt < MAX_RUN_DURATION.toNanos();
    }

    private BatchResult closeBatchInTransaction(
            QuestStatus from,
            QuestStatus to,
            LocalDateTime now,
            int limit,
            Set<Long> failedQuestIds) {
        BatchResult result = transactionTemplate.execute(
                status -> closeBatch(from, to, now, limit, failedQuestIds));
        return result == null ? BatchResult.EMPTY : result;
    }

    /** 한 묶음 = 한 트랜잭션. 커밋해야 잠금이 풀린다. */
    private BatchResult closeBatch(QuestStatus from,
                                   QuestStatus to,
                                   LocalDateTime now,
                                   int limit,
                                   Set<Long> failedQuestIds) {
        List<QuestVO> targets = questMapper.selectDeadlineTargetsForUpdate(
                from, now, limit, failedQuestIds);
        int attempted = 0;
        int changed = 0;
        for (QuestVO quest : targets) {
            attempted++;
            try {
                Integer updated = nestedTransactionTemplate.execute(
                        status -> closeQuest(quest, from, to, now));
                changed += updated == null ? 0 : updated;
            } catch (BusinessException e) {
                failedQuestIds.add(quest.getId());
                log.warn("퀘스트 마감 개별 처리 실패. questId={}, code={}",
                        quest.getId(), e.getErrorCode().getCode());
            } catch (DataIntegrityViolationException e) {
                failedQuestIds.add(quest.getId());
                log.warn("퀘스트 마감 데이터 오류. questId={}, type={}",
                        quest.getId(), e.getClass().getSimpleName());
            }
        }
        return new BatchResult(attempted, changed);
    }

    private int closeQuest(QuestVO quest,
                           QuestStatus from,
                           QuestStatus to,
                           LocalDateTime now) {
        Integer remainingCount = to == QuestStatus.FAILED ? 0 : null;
        int updated = questMapper.updateStatusForDeadline(
                quest.getId(), from, to, remainingCount, now);
        if (updated != 1) {
            throw new BusinessException(QuestErrorCode.QUEST_STATUS_CONFLICT);
        }
        if (from == QuestStatus.IN_PROGRESS
                && Boolean.TRUE.equals(quest.getTeenyScoreEnabled())) {
            applyFailureScore(quest);
        }
        return 1;
    }

    /**
     * 점수 반영만 따로 SAVEPOINT 로 감싼다.
     *
     * 점수 조회는 회원이 CHILD·ACTIVE 일 때만 성공한다. 퀘스트 생성 뒤 자녀가 비활성화되면
     * 점수를 줄 수 없는데, 그 이유로 마감까지 되돌리면 기한이 지난 퀘스트가 IN_PROGRESS 로
     * 남아 매 실행 같은 행을 다시 집는다. 점수만 생략하고 퀘스트는 마감한다.
     *
     * SAVEPOINT 로 감싸는 이유는, change() 가 참여 트랜잭션에서 실패하면 커넥션에
     * rollback-only 가 설정될 수 있어서다. 그대로 삼키고 커밋하면 묶음 전체가 터진다.
     */
    private void applyFailureScore(QuestVO quest) {
        try {
            nestedTransactionTemplate.execute(status ->
                    teenyScoreChangeService.change(
                            teenyScorePolicyService.questFailed(
                                    quest.getChildId(), quest.getId())));
        } catch (BusinessException e) {
            if (e.getErrorCode() != TeenyScoreErrorCode.TEENY_SCORE_CHILD_NOT_FOUND) {
                throw e;
            }
            log.warn("점수 대상 자녀가 아니라 점수 없이 마감한다. questId={}, childId={}",
                    quest.getId(), quest.getChildId());
        }
    }

    private record BatchResult(int attempted, int changed) {
        private static final BatchResult EMPTY = new BatchResult(0, 0);
    }
}
