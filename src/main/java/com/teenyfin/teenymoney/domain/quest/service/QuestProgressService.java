package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.dto.request.QuestDeclineRequestDTO;
import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.*;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import com.teenyfin.teenymoney.global.storage.S3Storage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class QuestProgressService {

    private static final String IMAGE_KEY_PREFIX = "quest-verifications/";
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final String VERIFICATION_PENDING = "PENDING";

    private final QuestMapper questMapper;
    private final QuestStatePolicy questStatePolicy;
    private final S3Storage s3Storage;

    // 인증 제출은 S3 업로드를 트랜잭션 밖에 둬야 해서 @Transactional 로 경계를 잡을 수 없다.
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public QuestProgressService(QuestMapper questMapper,
                                QuestStatePolicy questStatePolicy,
                                S3Storage s3Storage,
                                PlatformTransactionManager transactionManager,
                                Clock clock) {
        this.questMapper = questMapper;
        this.questStatePolicy = questStatePolicy;
        this.s3Storage = s3Storage;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // 자녀가 퀘스트를 수락한다.
    @Transactional
    public void accept(MemberPrincipal principal, Long questId) {
        Long childId = requireChild(principal);
        QuestVO quest = findOwnedForUpdate(questId, childId);
        LocalDateTime now = now();
        questStatePolicy.requireAvailableBeforeDeadline(quest, now);
        if (questMapper.updateStatusByChild(questId, childId, QuestStatus.AVAILABLE, QuestStatus.IN_PROGRESS, now) != 1) {
            throw new BusinessException(QuestErrorCode.QUEST_STATUS_CONFLICT);
        }
    }

    // 자녀가 퀘스트를 거절한다.
    @Transactional
    public void decline(MemberPrincipal principal, Long questId,
                        QuestDeclineRequestDTO request) {
        Long childId = requireChild(principal);

        // DB를 건드리기 전에 입력부터 확인. 틀린 요청 때문에 행을 잠글 필요 없으니까.
        DeclineReasonCode reasonCode = (request == null) ? null : request.getReasonCode();
        String reasonDetail = normalizeText((request == null) ? null : request.getReasonDetail());
        if (reasonCode == null || (reasonCode == DeclineReasonCode.OTHER && reasonDetail == null)) {
            throw new BusinessException(QuestErrorCode.QUEST_DECLINE_REASON_INVALID);
        }

        QuestVO quest = findOwnedForUpdate(questId, childId);
        LocalDateTime now = now();
        questStatePolicy.requireAvailableBeforeDeadline(quest, now);
        if (questMapper.updateDeclineByChild(questId, childId, reasonCode, reasonDetail, now) != 1) {
            throw new BusinessException(QuestErrorCode.QUEST_STATUS_CONFLICT);
        }
    }

    // IN_PROGRESS -> PENDING. 시도마다 새 인증 행을 넣고 이전 시도는 건드리지 않는다.
    @Transactional
    public void submitVerification(MemberPrincipal principal, Long questId, String rawContent) {
        Long childId = requireChild(principal);
        String content = normalizeText(rawContent);
        // multipart 파라미터라 @Size 를 걸 DTO 가 없다. 컬럼이 VARCHAR(500) 이므로 여기서 막는다.
        if (content != null && content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }

        QuestVO quest = findOwnedForUpdate(questId, childId);
        LocalDateTime now = now();
        requireSubmittable(quest, content != null, false, now);

        // 최신 시도를 재사용해 번호를 매긴다. (quest_id, attempt_no) UNIQUE 가 경합의 마지막 방어선이다.
        QuestVerificationVO latest = questMapper.selectLatestVerification(questId);
        QuestVerificationVO verification = QuestVerificationVO.builder()
                .questId(questId)
                .attemptNo(latest == null ? 1 : latest.getAttemptNo() + 1)
                .content(content)
                .status(VERIFICATION_PENDING)
                .createdAt(now)
                .build();
        if (questMapper.insertVerification(verification) != 1) {
            throw new IllegalStateException("인증 이력 생성 결과가 올바르지 않습니다.");
        }
        if (questMapper.updateStatusByChild(
                questId, childId, QuestStatus.IN_PROGRESS, QuestStatus.PENDING, now) != 1) {
            throw new BusinessException(QuestErrorCode.QUEST_STATUS_CONFLICT);
        }
    }


    // 공백만 있는 값은 없는 것으로 본다. DB에 "   "가 들어가면 설명이 있는 것처럼 보인다.
    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private QuestVO findOwnedForUpdate(Long questId, Long childId) {
        if (questId == null || questId <= 0) {
            throw new BusinessException(QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);
        }
        QuestVO quest = questMapper.selectByIdForUpdateByChild(questId, childId);
        if (quest == null) {
            throw new BusinessException(QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);
        }
        return quest;
    }

    // 자녀인지확인
    private Long requireChild(MemberPrincipal principal) {
        if (principal == null || !"CHILD".equals(principal.role())) {
            throw new BusinessException(QuestErrorCode.QUEST_CHILD_ONLY);
        }
        return principal.memberId();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * 제출 가능한 상태인지 본다.
     * IN_PROGRESS 조건이 "동시에 하나의 PENDING 인증만"(불변식 7)을 대신 지킨다.
     * 첫 제출이 퀘스트를 PENDING 으로 바꾸므로 중복 제출은 여기서 상태 충돌로 걸린다.
     */
    private void requireSubmittable(QuestVO quest, boolean hasText, boolean hasImage, LocalDateTime now) {
        questStatePolicy.requireStatusBeforeDeadline(quest, QuestStatus.IN_PROGRESS, now);
        if (quest.getRemainingCount() == null || quest.getRemainingCount() <= 0) {
            throw new BusinessException(QuestErrorCode.QUEST_VERIFICATION_ATTEMPT_EXCEEDED);
        }
        if (!meetsRequirement(quest.getVerificationRequirement(), hasText, hasImage)) {
            throw new BusinessException(QuestErrorCode.QUEST_VERIFICATION_REQUIREMENT_UNMET);
        }
    }

    private boolean meetsRequirement(VerificationRequirement requirement, boolean hasText, boolean hasImage) {
        if (requirement == null) {
            return false;
        }
        switch (requirement) {
            case FREE:
                return true;
            case PHOTO_REQUIRED:
                return hasImage;
            case TEXT_REQUIRED:
                return hasText;
            case ANY_REQUIRED:
                return hasText || hasImage;
            default:
                return false;
        }
    }
}

