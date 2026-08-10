package com.teenyfin.teenymoney.domain.quest.dto.response;

import com.teenyfin.teenymoney.domain.quest.vo.QuestVerificationVO;
import io.swagger.annotations.ApiModel;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@ApiModel(description = "가장 최근의 퀘스트 인증")
public class QuestVerificationResponseDTO {

    private final Long verificationId;
    private final Integer attemptNo;
    private final String content;
    private final String imageUrl;
    private final boolean imageExpired;
    private final String status;
    private final String rejectionReason;
    private final LocalDateTime submittedAt;
    private final LocalDateTime reviewedAt;

    private QuestVerificationResponseDTO(
            QuestVerificationVO verification, String imageUrl, boolean imageExpired) {
        this.verificationId = verification.getId();
        this.attemptNo = verification.getAttemptNo();
        this.content = verification.getContent();
        this.imageUrl = imageUrl;
        this.imageExpired = imageExpired;
        this.status = verification.getStatus();
        this.rejectionReason = verification.getRejectionReason();
        this.submittedAt = verification.getCreatedAt();
        this.reviewedAt = verification.getReviewedAt();
    }

    public static QuestVerificationResponseDTO of(
            QuestVerificationVO verification, String imageUrl, boolean imageExpired) {
        return new QuestVerificationResponseDTO(verification, imageUrl, imageExpired);
    }
}
