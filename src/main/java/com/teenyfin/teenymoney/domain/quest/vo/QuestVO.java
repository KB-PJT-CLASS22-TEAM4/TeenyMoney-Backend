package com.teenyfin.teenymoney.domain.quest.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestVO {
    private Long id;
    private Long parentId;
    private Long childId;
    private String creationRequestKey;
    private String childName;
    private String childProfileImageKey;
    private String title;
    private String content;
    private LocalDateTime deadline;
    private Boolean teenyScoreEnabled;
    private VerificationRequirement verificationRequirement;
    private Long rewardAmount;
    private QuestStatus status;
    private Integer remainingCount;
    private LocalDateTime acceptedAt;
    private String declineReasonCode;
    private String declineReasonDetail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime endedAt;
}
