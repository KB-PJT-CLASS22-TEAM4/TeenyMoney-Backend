package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class QuestDeadlineService {

    // 이슈가 정한 값. 규모가 커지면 여기 숫자만 바꾼다.
    private static final int LOCK_BATCH_SIZE = 200;
    private static final int MAX_PER_RUN = 2_000;
    private static final Duration MAX_RUN_DURATION = Duration.ofSeconds(20);

    private final QuestMapper questMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public QuestDeadlineService(QuestMapper questMapper,
                                PlatformTransactionManager transactionManager,
                                Clock clock) {
        this.questMapper = questMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** 한 차례 실행. 처리한 건수를 반환한다. */
    public int closeExpired() {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        long deadline = System.nanoTime() + MAX_RUN_DURATION.toNanos();
        int processed = 0;

        while (processed < MAX_PER_RUN && System.nanoTime() < deadline) {
            int batchSize = Math.min(LOCK_BATCH_SIZE, MAX_PER_RUN - processed);
            int done = transactionTemplate.execute(
                    status -> closeBatch(QuestStatus.AVAILABLE, QuestStatus.EXPIRED, now, batchSize));
            if (done == 0) {
                break;          // 대상 소진
            }
            processed += done;
        }
        return processed;
    }

    /** 한 묶음 = 한 트랜잭션. 커밋해야 잠금이 풀린다. */
    private int closeBatch(QuestStatus from, QuestStatus to, LocalDateTime now, int limit) {
        List<QuestVO> targets =
                questMapper.selectDeadlineTargetsForUpdate(from, now, limit);
        int changed = 0;
        for (QuestVO quest : targets) {
            changed += questMapper.updateStatusForDeadline(quest.getId(), from, to, now);
        }
        return changed;
    }
}