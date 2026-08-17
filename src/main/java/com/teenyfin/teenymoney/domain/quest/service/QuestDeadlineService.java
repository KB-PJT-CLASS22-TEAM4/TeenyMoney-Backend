package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.member.vo.MemberVO;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QuestDeadlineService {

    private static final Logger log = LoggerFactory.getLogger(QuestDeadlineService.class);
    // 이슈가 정한 값. 규모가 커지면 여기 숫자만 바꾼다.
    private static final int LOCK_BATCH_SIZE = 200;
    private static final int MAX_PER_RUN = 2_000;
    private static final Duration MAX_RUN_DURATION = Duration.ofSeconds(20);
    private static final String QUEST_NOT_STARTED_TITLE = "자녀가 퀘스트를 시작하지 않았어요";
    private static final String QUEST_FAILED_TITLE = "퀘스트가 실패했어요";

    private final QuestMapper questMapper;
    private final TeenyScorePolicyService teenyScorePolicyService;
    private final TeenyScoreChangeService teenyScoreChangeService;
    // 부모에게 보내는 알림에 자녀 이름을 담는다. 마감 대상 조회는 FOR UPDATE 라 조인을 붙일 수 없다.
    private final MemberMapper memberMapper;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate nestedTransactionTemplate;
    private final Clock clock;

    public QuestDeadlineService(QuestMapper questMapper,
                                TeenyScorePolicyService teenyScorePolicyService,
                                TeenyScoreChangeService teenyScoreChangeService,
                                MemberMapper memberMapper,
                                NotificationService notificationService,
                                PlatformTransactionManager transactionManager,
                                Clock clock) {
        this.questMapper = questMapper;
        this.teenyScorePolicyService = teenyScorePolicyService;
        this.teenyScoreChangeService = teenyScoreChangeService;
        this.memberMapper = memberMapper;
        this.notificationService = notificationService;
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
        // 커밋이 끝난 마감 건만 모은다. 알림은 루프가 끝난 뒤에 보낸다.
        List<ClosedQuest> closed = new ArrayList<>();
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
                            failedQuestIds,
                            closed);
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
                            failedQuestIds,
                            closed);
                    attempted += result.attempted();
                    processed += result.changed();
                    inProgressExhausted = result.attempted() == 0;
                }
            }
        } catch (RuntimeException e) {
            // 이미 커밋된 묶음의 건수가 예외와 함께 사라진다. 숫자만 남기고 스택은 스케줄러가 찍는다.
            log.warn("퀘스트 마감 중단. 중단 전까지 {}건 처리", processed);
            throw e;
        } finally {
            // 루프가 중간에 끊겨도 이미 마감된 건은 알려야 한다. 마감 자체는 커밋이 끝났다.
            notifyClosed(closed);
        }
        return processed;
    }

    /**
     * 마감 결과를 알린다. 전이에 따라 받는 사람이 다르다.
     * 수락도 하지 않은 채 끝난 건은 부모가 알아야 할 정보고, 수행하다 못 끝낸 건은 자녀의 결과다.
     *
     * 알림은 부가 작업이다. 여기서 터져도 마감은 이미 커밋됐으므로 건별로 삼키고 로그만 남긴다
     * (AllowanceScheduleProcessor.notifyBestEffort 와 같은 취지).
     */
    private void notifyClosed(List<ClosedQuest> closed) {
        Map<Long, String> childNames = new HashMap<>();
        for (ClosedQuest each : closed) {
            try {
                if (each.finalStatus() == QuestStatus.FAILED) {
                    notifyChildOfFailure(each.quest());
                } else {
                    notifyParentOfNoStart(each.quest(), childNames);
                }
            } catch (RuntimeException e) {
                log.warn("퀘스트 마감 알림 실패. questId={}", each.quest().getId(), e);
            }
        }
    }

    private void notifyChildOfFailure(QuestVO quest) {
        // 점수 퀘스트가 아니면 차감 문구가 거짓이 된다.
        String content = Boolean.TRUE.equals(quest.getTeenyScoreEnabled())
                ? quest.getTitle() + " · 티니점수가 차감됐어요"
                : quest.getTitle();
        notificationService.createNotification(
                quest.getChildId(),
                QUEST_FAILED_TITLE,
                content,
                NotificationReferenceType.QUEST,
                quest.getId(),
                true);
    }

    private void notifyParentOfNoStart(QuestVO quest, Map<Long, String> childNames) {
        // 이름을 제목이 아니라 내용 앞에 두는 것은 PermissionService 와 같은 형식이다.
        // 제목에 이름을 넣으면 받침에 따라 조사를 골라야 한다.
        String childName = childName(quest.getChildId(), childNames);
        String content = childName == null
                ? quest.getTitle()
                : childName + " · " + quest.getTitle();
        notificationService.createNotification(
                quest.getParentId(),
                QUEST_NOT_STARTED_TITLE,
                content,
                NotificationReferenceType.QUEST,
                quest.getId(),
                true);
    }

    /** 같은 자녀의 퀘스트가 여러 건 마감될 수 있다. 이름은 자녀당 한 번만 조회한다. */
    private String childName(Long childId, Map<Long, String> cache) {
        if (childId == null) {
            return null;
        }
        return cache.computeIfAbsent(childId, id -> {
            MemberVO child = memberMapper.selectById(id);
            return child == null ? null : child.getName();
        });
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
            Set<Long> failedQuestIds,
            List<ClosedQuest> closed) {
        BatchResult result = transactionTemplate.execute(
                status -> closeBatch(from, to, now, limit, failedQuestIds));
        if (result == null) {
            return BatchResult.EMPTY;
        }
        // 커밋된 뒤에만 알림 대상에 넣는다. 묶음이 통째로 롤백되면 알리지 않는다.
        closed.addAll(result.closed());
        return result;
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
        List<ClosedQuest> closed = new ArrayList<>();
        for (QuestVO quest : targets) {
            attempted++;
            try {
                Integer updated = nestedTransactionTemplate.execute(
                        status -> closeQuest(quest, from, to, now));
                if (updated != null && updated > 0) {
                    changed += updated;
                    closed.add(new ClosedQuest(quest, to));
                }
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
        return new BatchResult(attempted, changed, closed);
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

    private record BatchResult(int attempted, int changed, List<ClosedQuest> closed) {
        private static final BatchResult EMPTY = new BatchResult(0, 0, List.of());
    }

    /** 마감이 확정된 퀘스트와 그 최종 상태. QuestVO.status 는 조회 시점 값이라 따로 들고 있어야 한다. */
    private record ClosedQuest(QuestVO quest, QuestStatus finalStatus) {
    }
}
