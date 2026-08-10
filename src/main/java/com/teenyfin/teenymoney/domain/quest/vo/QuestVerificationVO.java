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
public class QuestVerificationVO {
    private Long id;
    private Long questId;
    private Integer attemptNo;
    private String imageKey;
    private String content;
    private String status;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
