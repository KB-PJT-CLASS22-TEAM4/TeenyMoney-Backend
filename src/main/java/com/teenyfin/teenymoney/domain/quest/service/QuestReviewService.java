package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class QuestReviewService {

    private static final String VERIFICATION_PENDING = "PENDING";
    private static final String VERIFICATION_APPROVED = "APPROVED";
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
}
