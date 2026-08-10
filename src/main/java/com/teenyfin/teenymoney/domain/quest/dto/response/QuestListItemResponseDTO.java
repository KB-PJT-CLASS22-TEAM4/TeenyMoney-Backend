package com.teenyfin.teenymoney.domain.quest.dto.response;

import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.quest.vo.VerificationRequirement;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@ApiModel(description = "퀘스트 목록 항목")
public class QuestListItemResponseDTO {

    @ApiModelProperty(value = "퀘스트 ID", example = "55")
    private final Long questId;
    private final QuestChildResponseDTO child;
    private final String title;
    private final LocalDateTime deadline;
    private final Long rewardAmount;
    private final Boolean teenyScoreEnabled;
    private final VerificationRequirement verificationRequirement;
    private final QuestStatus status;
    private final Integer remainingCount;
    private final LocalDateTime endedAt;

    private QuestListItemResponseDTO(QuestVO quest, QuestChildResponseDTO child) {
        this.questId = quest.getId();
        this.child = child;
        this.title = quest.getTitle();
        this.deadline = quest.getDeadline();
        this.rewardAmount = quest.getRewardAmount();
        this.teenyScoreEnabled = quest.getTeenyScoreEnabled();
        this.verificationRequirement = quest.getVerificationRequirement();
        this.status = quest.getStatus();
        this.remainingCount = quest.getRemainingCount();
        this.endedAt = quest.getEndedAt();
    }

    public static QuestListItemResponseDTO of(QuestVO quest, String profileImageUrl) {
        QuestChildResponseDTO child = new QuestChildResponseDTO(
                quest.getChildId(), quest.getChildName(), profileImageUrl);
        return new QuestListItemResponseDTO(quest, child);
    }
}
