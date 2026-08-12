package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.dto.request.QuestRejectRequestDTO;
import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.AfterDeadlineAction;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVerificationVO;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreChangeService;
import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScorePolicyService;
import com.teenyfin.teenymoney.domain.wallet.exception.WalletErrorCode;
import com.teenyfin.teenymoney.domain.wallet.mapper.WalletMapper;
import com.teenyfin.teenymoney.domain.wallet.service.TransferService;
import com.teenyfin.teenymoney.domain.wallet.vo.TransferType;
import com.teenyfin.teenymoney.domain.wallet.vo.WalletVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class QuestReviewService {

    // 승인·반려 실패는 트랜잭션이 롤백되어 DB에 아무 흔적이 남지 않는다.
    // 운영 중 "승인이 안 된다"는 문의가 오면 이 로그가 유일한 단서다.
    private static final Logger log = LoggerFactory.getLogger(QuestReviewService.class);

    private static final String VERIFICATION_PENDING = "PENDING";
    private static final String VERIFICATION_APPROVED = "APPROVED";
    private static final String VERIFICATION_REJECTED = "REJECTED";
    private static final String QUEST_REWARD_KEY_PREFIX = "QUEST_REWARD:";

    private final QuestMapper questMapper;
    private final WalletMapper walletMapper;
    private final TransferService transferService;
    private final TeenyScorePolicyService teenyScorePolicyService;
    private final TeenyScoreChangeService teenyScoreChangeService;
    private final Clock clock;

    public QuestReviewService(
            QuestMapper questMapper,
            WalletMapper walletMapper,
            TransferService transferService,
            TeenyScorePolicyService teenyScorePolicyService,
            TeenyScoreChangeService teenyScoreChangeService,
            Clock clock) {
        this.questMapper = questMapper;
        this.walletMapper = walletMapper;
        this.transferService = transferService;
        this.teenyScorePolicyService = teenyScorePolicyService;
        this.teenyScoreChangeService = teenyScoreChangeService;
        this.clock = clock;
    }

    /**
     * 최신 PENDING 인증을 승인하고 보상, 점수, 상태를 한 트랜잭션으로 확정한다.
     */
    @Transactional
    public void approve(
            MemberPrincipal principal,
            Long questId,
            Long verificationId) {
        Long parentId = requireParent(principal);
        try {
            QuestVO quest = findOwnedPendingForUpdate(questId, parentId);
            QuestVerificationVO verification =
                    findLatestPendingVerificationForUpdate(
                            questId, verificationId);
            LocalDateTime now = now();

            applyRewardIfPresent(quest);
            if (Boolean.TRUE.equals(quest.getTeenyScoreEnabled())) {
                teenyScoreChangeService.change(
                        teenyScorePolicyService.questCompleted(
                                quest.getChildId(), quest.getId()));
            }

            if (questMapper.updateVerificationReview(
                    verification.getId(),
                    quest.getId(),
                    VERIFICATION_APPROVED,
                    null,
                    now) != 1) {
                throw new BusinessException(
                        QuestErrorCode.QUEST_VERIFICATION_CONFLICT);
            }
            if (questMapper.updateCompletedByParent(
                    quest.getId(), parentId, now, now) != 1) {
                throw new BusinessException(QuestErrorCode.QUEST_STATUS_CONFLICT);
            }
        } catch (BusinessException e) {
            logReviewFailure("승인", questId, verificationId, parentId, e.getErrorCode().getCode(), e);
            throw e;
        } catch (RuntimeException e) {
            logReviewFailure("승인", questId, verificationId, parentId, "UNEXPECTED", e);
            throw e;
        }
    }

    /**
     * 최신 PENDING 인증을 반려한다. 기회가 남으면 다시 수행 상태로 열고,
     * 마지막 기회이거나 기한 후 실패를 선택하면 최종 실패로 확정한다.
     */
    @Transactional
    public void reject(
            MemberPrincipal principal,
            Long questId,
            Long verificationId,
            QuestRejectRequestDTO request) {
        Long parentId = requireParent(principal);
        try {
            rejectInternal(principal, questId, verificationId, request, parentId);
        } catch (BusinessException e) {
            logReviewFailure("반려", questId, verificationId, parentId, e.getErrorCode().getCode(), e);
            throw e;
        } catch (RuntimeException e) {
            logReviewFailure("반려", questId, verificationId, parentId, "UNEXPECTED", e);
            throw e;
        }
    }

    private void rejectInternal(
            MemberPrincipal principal,
            Long questId,
            Long verificationId,
            QuestRejectRequestDTO request,
            Long parentId) {
        String reason = normalizeReason(request);
        QuestVO quest = findOwnedPendingForUpdate(questId, parentId);
        QuestVerificationVO verification =
                findLatestPendingVerificationForUpdate(
                        questId, verificationId);
        LocalDateTime now = now();

        Integer remainingCount = quest.getRemainingCount();
        if (remainingCount == null || remainingCount <= 0) {
            throw new BusinessException(
                    QuestErrorCode.QUEST_VERIFICATION_ATTEMPT_EXCEEDED);
        }
        int nextRemainingCount = remainingCount - 1;
        RejectionDecision decision = decideRejection(
                quest, request, nextRemainingCount, now);
        int remainingCountAfterReview =
                decision.status == QuestStatus.FAILED
                        ? 0 : nextRemainingCount;

        if (decision.status == QuestStatus.FAILED
                && Boolean.TRUE.equals(quest.getTeenyScoreEnabled())) {
            teenyScoreChangeService.change(
                    teenyScorePolicyService.questFailed(
                            quest.getChildId(), quest.getId()));
        }
        if (questMapper.updateVerificationReview(
                verification.getId(),
                quest.getId(),
                VERIFICATION_REJECTED,
                reason,
                now) != 1) {
            throw new BusinessException(
                    QuestErrorCode.QUEST_VERIFICATION_CONFLICT);
        }
        if (questMapper.updateAfterRejectionByParent(
                quest.getId(),
                parentId,
                decision.status,
                remainingCountAfterReview,
                decision.deadline,
                decision.endedAt,
                now) != 1) {
            throw new BusinessException(QuestErrorCode.QUEST_STATUS_CONFLICT);
        }
    }

    private RejectionDecision decideRejection(
            QuestVO quest,
            QuestRejectRequestDTO request,
            int nextRemainingCount,
            LocalDateTime now) {
        // 마지막 인증 반려는 연장 선택지가 없다(설계 8.1).
        //
        // 값이 왔다면 클라이언트가 남은 횟수를 잘못 알고 있다는 뜻이다. 조용히 무시하면
        // 부모는 연장해 준 줄 알고, 자녀는 최종 실패로 티니점수까지 잃는다. 종료 상태라
        // 되돌릴 수도 없다. 기한 전에 불필요한 값이 왔을 때와 같은 기준으로 거절한다.
        // normalizeReason 과 마찬가지로 request 가 null 인 경우까지 견딘다.
        AfterDeadlineAction action =
                request == null ? null : request.getAfterDeadlineAction();
        LocalDateTime extendedDeadline =
                request == null ? null : request.getExtendedDeadline();

        if (nextRemainingCount == 0) {
            if (action != null || extendedDeadline != null) {
                throw new BusinessException(
                        QuestErrorCode.QUEST_REVIEW_REQUEST_INVALID);
            }
            return RejectionDecision.failed(now);
        }

        LocalDateTime currentDeadline = quest.getDeadline();
        if (currentDeadline == null) {
            throw new BusinessException(
                    QuestErrorCode.QUEST_REVIEW_REQUEST_INVALID);
        }
        boolean afterDeadline = now.isAfter(currentDeadline);

        if (!afterDeadline) {
            if (action != null || extendedDeadline != null) {
                throw new BusinessException(
                        QuestErrorCode.QUEST_REVIEW_REQUEST_INVALID);
            }
            return RejectionDecision.retry(null);
        }

        if (action == null) {
            throw new BusinessException(
                    QuestErrorCode.QUEST_REVIEW_REQUEST_INVALID);
        }
        if (action == AfterDeadlineAction.FAIL) {
            if (extendedDeadline != null) {
                throw new BusinessException(
                        QuestErrorCode.QUEST_REVIEW_REQUEST_INVALID);
            }
            return RejectionDecision.failed(now);
        }
        if (extendedDeadline == null
                || !extendedDeadline.isAfter(now)
                || extendedDeadline.isAfter(now.plusYears(1))) {
            throw new BusinessException(
                    QuestErrorCode.QUEST_EXTENDED_DEADLINE_INVALID);
        }
        return RejectionDecision.retry(extendedDeadline);
    }

    /**
     * 검토 실패를 남긴다. 트랜잭션이 롤백되면 DB 에는 아무것도 남지 않으므로 로그가 유일한 기록이다.
     *
     * 예외는 다시 던진다. 여기서 삼키면 실패한 승인이 성공으로 응답된다.
     */
    private void logReviewFailure(
            String action,
            Long questId,
            Long verificationId,
            Long parentId,
            String reasonCode,
            RuntimeException cause) {
        log.warn("퀘스트 {} 실패 questId={} verificationId={} parentId={} reason={} message={}",
                action, questId, verificationId, parentId, reasonCode, cause.getMessage());
    }

    private String normalizeReason(QuestRejectRequestDTO request) {
        if (request == null || request.getReason() == null) {
            return null;
        }
        String reason = request.getReason().trim();
        if (reason.isEmpty()) {
            return null;
        }

        if (reason.length() > 500) {
            throw new BusinessException(
                    QuestErrorCode.QUEST_REVIEW_REQUEST_INVALID);
        }
        return reason;
    }

    private void applyRewardIfPresent(QuestVO quest) {
        Long rewardAmount = quest.getRewardAmount();
        if (rewardAmount == null || rewardAmount == 0) {
            return;
        }
        WalletVO parentWallet = walletMapper.selectMemberWalletByMemberId(
                quest.getParentId());
        WalletVO childWallet = walletMapper.selectMemberWalletByMemberId(
                quest.getChildId());
        if (parentWallet == null || childWallet == null) {
            throw new BusinessException(WalletErrorCode.WALLET_NOT_FOUND);
        }
        transferService.transferInExistingTransaction(
                parentWallet.getId(),
                childWallet.getId(),
                rewardAmount,
                TransferType.QUEST_REWARD,
                QUEST_REWARD_KEY_PREFIX + quest.getId());
    }

    private QuestVO findOwnedPendingForUpdate(
            Long questId,
            Long parentId) {
        if (questId == null || questId <= 0) {
            throw new BusinessException(
                    QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);
        }
        QuestVO quest = questMapper.selectByIdForUpdateByParent(
                questId, parentId);
        if (quest == null) {
            throw new BusinessException(
                    QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);
        }
        if (quest.getStatus() != QuestStatus.PENDING) {
            throw new BusinessException(QuestErrorCode.QUEST_STATUS_CONFLICT);
        }
        return quest;
    }

    private QuestVerificationVO findLatestPendingVerificationForUpdate(
            Long questId,
            Long verificationId) {
        if (verificationId == null || verificationId <= 0) {
            throw new BusinessException(
                    QuestErrorCode.QUEST_VERIFICATION_CONFLICT);
        }
        QuestVerificationVO latest =
                questMapper.selectLatestVerificationForUpdate(questId);
        if (latest == null
                || !verificationId.equals(latest.getId())
                || !VERIFICATION_PENDING.equals(latest.getStatus())) {
            throw new BusinessException(
                    QuestErrorCode.QUEST_VERIFICATION_CONFLICT);
        }
        return latest;
    }

    private Long requireParent(MemberPrincipal principal) {
        if (principal == null || !"PARENT".equals(principal.role())) {
            throw new BusinessException(QuestErrorCode.QUEST_PARENT_ONLY);
        }
        return principal.memberId();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private static class RejectionDecision {
        private final QuestStatus status;
        private final LocalDateTime deadline;
        private final LocalDateTime endedAt;

        private RejectionDecision(
                QuestStatus status,
                LocalDateTime deadline,
                LocalDateTime endedAt) {
            this.status = status;
            this.deadline = deadline;
            this.endedAt = endedAt;
        }

        private static RejectionDecision retry(LocalDateTime deadline) {
            return new RejectionDecision(
                    QuestStatus.IN_PROGRESS, deadline, null);
        }

        private static RejectionDecision failed(LocalDateTime endedAt) {
            return new RejectionDecision(
                    QuestStatus.FAILED, null, endedAt);
        }
    }
}
