package com.teenyfin.teenymoney.domain.quest.dto.response;

import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.quest.vo.VerificationRequirement;
import io.swagger.annotations.ApiModel;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@ApiModel(description = "퀘스트 상세")
public class QuestDetailResponseDTO {

    private final Long questId;
    private final QuestChildResponseDTO child;
    private final String title;
    private final String content;
    private final LocalDateTime deadline;
    private final Long rewardAmount;
    private final Boolean teenyScoreEnabled;
    private final VerificationRequirement verificationRequirement;
    private final QuestStatus status;
    private final Integer remainingCount;
    private final LocalDateTime createdAt;
    private final LocalDateTime endedAt;
    private final String declineReasonCode;
    private final String declineReasonDetail;
    private final QuestVerificationResponseDTO latestVerification;

    private QuestDetailResponseDTO(
            QuestVO quest,
            QuestChildResponseDTO child,
            QuestVerificationResponseDTO latestVerification) {
        this.questId = quest.getId();
        this.child = child;
        this.title = quest.getTitle();
        this.content = quest.getContent();
        this.deadline = quest.getDeadline();
        this.rewardAmount = quest.getRewardAmount();
        this.teenyScoreEnabled = quest.getTeenyScoreEnabled();
        this.verificationRequirement = quest.getVerificationRequirement();
        this.status = quest.getStatus();
        this.remainingCount = quest.getRemainingCount();
        this.createdAt = quest.getCreatedAt();
        this.endedAt = quest.getEndedAt();
        this.declineReasonCode = quest.getDeclineReasonCode();
        this.declineReasonDetail = quest.getDeclineReasonDetail();
        this.latestVerification = latestVerification;
    }

    public static QuestDetailResponseDTO of(
            QuestVO quest,
            String profileImageUrl,
            QuestVerificationResponseDTO latestVerification) {
        QuestChildResponseDTO child = new QuestChildResponseDTO(
                quest.getChildId(), quest.getChildName(), profileImageUrl);
        return new QuestDetailResponseDTO(quest, child, latestVerification);
    }
}
