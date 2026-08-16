package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestDeclineRequestDTO;
import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.*;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import com.teenyfin.teenymoney.global.storage.ImageFile;
import com.teenyfin.teenymoney.global.storage.S3Storage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class QuestProgressService {

    private static final String IMAGE_KEY_PREFIX = "quest-verifications/";
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final String VERIFICATION_PENDING = "PENDING";
    private static final String QUEST_ACCEPTED_TITLE = "자녀가 퀘스트를 수락했어요";
    private static final String QUEST_DECLINED_TITLE = "자녀가 퀘스트를 거절했어요";
    private static final String VERIFICATION_SUBMITTED_TITLE = "인증을 확인해 주세요";

    private final QuestMapper questMapper;
    private final QuestStatePolicy questStatePolicy;
    private final S3Storage s3Storage;
    // 부모에게 보내는 알림에 자녀 이름을 담는다. QuestVO 의 FOR UPDATE 조회에는 이름이 없다.
    private final MemberMapper memberMapper;
    private final NotificationService notificationService;

    // 인증 제출은 S3 업로드를 트랜잭션 밖에 둬야 해서 @Transactional 로 경계를 잡을 수 없다.
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public QuestProgressService(QuestMapper questMapper,
                                QuestStatePolicy questStatePolicy,
                                S3Storage s3Storage,
                                MemberMapper memberMapper,
                                NotificationService notificationService,
                                PlatformTransactionManager transactionManager,
                                Clock clock) {
        this.questMapper = questMapper;
        this.questStatePolicy = questStatePolicy;
        this.s3Storage = s3Storage;
        this.memberMapper = memberMapper;
        this.notificationService = notificationService;
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
        notifyParent(quest, QUEST_ACCEPTED_TITLE, quest.getTitle());
    }

    // 자녀가 퀘스트를 거절한다.
    @Transactional
    public void decline(
            MemberPrincipal principal, Long questId, QuestDeclineRequestDTO request) {
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
        // 자녀가 직접 쓴 사유가 있으면 그대로 보여 주고, 없으면 고른 항목의 설명을 대신 쓴다.
        notifyParent(quest, QUEST_DECLINED_TITLE,
                reasonDetail != null ? reasonDetail : reasonCode.getLabel());
    }

    /**
     * IN_PROGRESS -> PENDING. 시도마다 새 인증 행을 넣고 이전 시도는 건드리지 않는다.
     * <p>
     * S3 업로드는 DB 트랜잭션과 묶을 수 없다. 업로드를 먼저 하고 DB를 나중에 하되,
     * 롤백되면 키를 저장하지 않으므로 그 오브젝트는 어떤 화면에도 나타나지 않는다.
     * 남은 고아 객체는 90일 Lifecycle 이 지운다(우리에겐 s3:DeleteObject 권한이 없다).
     */
    public void submitVerification(
            MemberPrincipal principal, Long questId, String rawContent, MultipartFile image) {
        Long childId = requireChild(principal);
        String content = normalizeText(rawContent);
        // multipart 파라미터라 @Size 를 걸 DTO 가 없다. 컬럼이 VARCHAR(500) 이므로 여기서 막는다.
        if (content != null && content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }

        // 이미지가 있을 때만 사전 검증 필요 (transaction이 안되기 때문). 상태가 이미 틀렸는데 올리면 지울수 없는(90일간) 오브젝트가 남는다.
        String imageKey = null;
        if (image != null && !image.isEmpty()) {
            ImageFile validated = ImageFile.validate(image);
            QuestVO preview = questMapper.selectDetailByChild(questId, childId);
            if (preview == null) {
                throw new BusinessException(QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);
            }
            requireSubmittable(preview, content != null, true, now());
            imageKey = s3Storage.upload(IMAGE_KEY_PREFIX + questId, validated);
        }
        // 람다가 잡는 값은 effectively final 이어야 한다.
        String storedKey = imageKey;
        QuestVO quest = transactionTemplate.execute(
                status -> recordSubmission(questId, childId, content, storedKey));

        // 여기만 커밋이 끝난 뒤에 알림이 나간다. 트랜잭션 경계를 밖에서 직접 열기 때문이다.
        notifyParent(quest, VERIFICATION_SUBMITTED_TITLE, quest.getTitle());
    }

    // transactionTemplate 안에서만 호출한다. @Transactional 을 붙여도 자기호출이라 무시된다.
    private QuestVO recordSubmission(Long questId, Long childId, String content, String imageKey) {
        QuestVO quest = findOwnedForUpdate(questId, childId);
        LocalDateTime now = now();
        requireSubmittable(quest, content != null, imageKey != null, now);

        // 최신 시도를 재사용해 번호를 매긴다. (quest_id, attempt_no) UNIQUE 가 경합의 마지막 방어선이다.
        QuestVerificationVO latest = questMapper.selectLatestVerification(questId);
        QuestVerificationVO verification = QuestVerificationVO.builder()
                .questId(questId)
                .attemptNo(latest == null ? 1 : latest.getAttemptNo() + 1)
                .imageKey(imageKey)
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
        return quest;
    }

    /**
     * 부모에게 알린다. 내용은 "자녀이름 · 상세" 형식으로 맞춘다.
     *
     * 이름을 제목이 아니라 내용에 두는 것은 PermissionService 와 같은 형식이다.
     * 제목에 이름을 넣으면 받침에 따라 조사를 골라야 한다.
     */
    private void notifyParent(QuestVO quest, String title, String detail) {
        String childName = memberMapper.selectById(quest.getChildId()).getName();
        notificationService.createNotification(
                quest.getParentId(),
                title,
                childName + " · " + detail,
                NotificationReferenceType.QUEST,
                quest.getId(),
                true);
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

