package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.domain.member.mapper.MemberMapper;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestCreateRequestDTO;
import com.teenyfin.teenymoney.domain.quest.dto.request.QuestUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.quest.vo.VerificationRequirement;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class QuestCreationService {

    private final QuestMapper questMapper;
    private final MemberMapper memberMapper;
    private final FamilyAccessService familyAccessService;
    private final QuestStatePolicy questStatePolicy;
    private final Clock clock;

    public QuestCreationService(
            QuestMapper questMapper,
            MemberMapper memberMapper,
            FamilyAccessService familyAccessService,
            QuestStatePolicy questStatePolicy,
            Clock clock) {
        this.questMapper = questMapper;
        this.memberMapper = memberMapper;
        this.familyAccessService = familyAccessService;
        this.questStatePolicy = questStatePolicy;
        this.clock = clock;
    }

    @Transactional
    public List<Long> create(
            MemberPrincipal principal,
            QuestCreateRequestDTO request,
            String requestKey) {
        requireParent(principal);
        String canonicalKey = canonicalUuid(requestKey);
        NormalizedQuest normalized = normalizeAndValidate(request);

        if (memberMapper.selectByIdForUpdate(principal.memberId()) == null) {
            throw new BusinessException(QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);
        }

        List<QuestVO> existing = questMapper.selectByCreationRequestKey(
                principal.memberId(), canonicalKey);
        if (!existing.isEmpty()) {
            return resolveRetry(existing, normalized);
        }

        for (Long childId : normalized.childIds()) {
            requireLinkedChild(principal, childId);
        }

        LocalDateTime createdAt = now();
        List<Long> questIds = new ArrayList<>(normalized.childIds().size());
        for (Long childId : normalized.childIds()) {
            QuestVO quest = QuestVO.builder()
                    .parentId(principal.memberId())
                    .childId(childId)
                    .creationRequestKey(canonicalKey)
                    .title(normalized.title())
                    .content(normalized.content())
                    .deadline(normalized.deadline())
                    .teenyScoreEnabled(normalized.teenyScoreEnabled())
                    .verificationRequirement(normalized.verificationRequirement())
                    .rewardAmount(normalized.rewardAmount())
                    .status(QuestStatus.AVAILABLE)
                    .remainingCount(3)
                    .createdAt(createdAt)
                    .build();
            if (questMapper.insert(quest) != 1 || quest.getId() == null) {
                throw new IllegalStateException("퀘스트 생성 결과가 올바르지 않습니다.");
            }
            questIds.add(quest.getId());
        }
        return questIds;
    }

    @Transactional
    public void update(
            MemberPrincipal principal,
            Long questId,
            QuestUpdateRequestDTO request) {
        requireParent(principal);
        QuestVO current = findOwnedForUpdate(principal.memberId(), questId);
        LocalDateTime now = now();
        questStatePolicy.requireAvailableBeforeDeadline(current, now);
        NormalizedFields fields = normalizeFields(
                request == null ? null : request.getTitle(),
                request == null ? null : request.getContent(),
                request == null ? null : request.getDeadline(),
                request == null ? null : request.getRewardAmount(),
                request == null ? null : request.getTeenyScoreEnabled(),
                request == null ? null : request.getVerificationRequirement(),
                1,
                true);

        QuestVO update = QuestVO.builder()
                .id(questId)
                .parentId(principal.memberId())
                .title(fields.title())
                .content(fields.content())
                .deadline(fields.deadline())
                .rewardAmount(fields.rewardAmount())
                .teenyScoreEnabled(fields.teenyScoreEnabled())
                .verificationRequirement(fields.verificationRequirement())
                .updatedAt(now)
                .build();
        if (questMapper.updateAvailable(update) != 1) {
            throw new BusinessException(QuestErrorCode.QUEST_STATUS_CONFLICT);
        }
    }

    @Transactional
    public void delete(MemberPrincipal principal, Long questId) {
        requireParent(principal);
        QuestVO current = findOwnedForUpdate(principal.memberId(), questId);
        questStatePolicy.requireAvailableBeforeDeadline(current, now());
        if (questMapper.deleteAvailable(questId, principal.memberId()) != 1) {
            throw new BusinessException(QuestErrorCode.QUEST_STATUS_CONFLICT);
        }
    }

    private void requireParent(MemberPrincipal principal) {
        if (principal == null || !"PARENT".equals(principal.role())) {
            throw new BusinessException(QuestErrorCode.QUEST_PARENT_ONLY);
        }
    }

    private String canonicalUuid(String rawKey) {
        if (rawKey == null) {
            throw new BusinessException(QuestErrorCode.QUEST_CREATION_KEY_INVALID);
        }
        String value = rawKey.trim();
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("noncanonical UUID");
            }
            return uuid.toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(QuestErrorCode.QUEST_CREATION_KEY_INVALID);
        }
    }

    private NormalizedQuest normalizeAndValidate(QuestCreateRequestDTO request) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }

        List<Long> childIds = request.getChildIds();
        if (childIds == null || childIds.isEmpty()) {
            throw new BusinessException(QuestErrorCode.QUEST_CHILD_REQUIRED);
        }
        Set<Long> uniqueChildIds = new HashSet<>();
        for (Long childId : childIds) {
            if (childId == null || childId <= 0) {
                throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
            }
            if (!uniqueChildIds.add(childId)) {
                throw new BusinessException(QuestErrorCode.QUEST_CHILD_DUPLICATED);
            }
        }

        NormalizedFields fields = normalizeFields(
                request.getTitle(),
                request.getContent(),
                request.getDeadline(),
                request.getRewardAmount(),
                request.getTeenyScoreEnabled(),
                request.getVerificationRequirement(),
                childIds.size(),
                false);

        return new NormalizedQuest(
                List.copyOf(childIds),
                fields.title(),
                fields.content(),
                fields.deadline(),
                fields.rewardAmount(),
                fields.teenyScoreEnabled(),
                fields.verificationRequirement());
    }

    private NormalizedFields normalizeFields(
            String rawTitle,
            String rawContent,
            LocalDateTime rawDeadline,
            Long rewardAmount,
            Boolean teenyScoreEnabled,
            VerificationRequirement requirement,
            int childCount,
            boolean allowCurrentSecond) {
        String title = normalizeRequiredText(rawTitle, 50);
        String content = normalizeRequiredText(rawContent, 500);
        if (rewardAmount == null || rewardAmount < 0
                || (rewardAmount > 0 && rewardAmount < 100)
                || (rewardAmount == 0 && !Boolean.TRUE.equals(teenyScoreEnabled))) {
            throw new BusinessException(QuestErrorCode.QUEST_REWARD_INVALID);
        }
        try {
            Math.multiplyExact(rewardAmount, childCount);
        } catch (ArithmeticException exception) {
            throw new BusinessException(QuestErrorCode.QUEST_REWARD_INVALID);
        }

        LocalDateTime deadline = rawDeadline;
        LocalDateTime now = now();
        if (deadline == null) {
            throw new BusinessException(QuestErrorCode.QUEST_DEADLINE_INVALID);
        }
        deadline = deadline.truncatedTo(ChronoUnit.SECONDS);
        boolean tooEarly = allowCurrentSecond ? deadline.isBefore(now) : !deadline.isAfter(now);
        if (tooEarly || deadline.isAfter(now.plusYears(1))) {
            throw new BusinessException(QuestErrorCode.QUEST_DEADLINE_INVALID);
        }

        if (teenyScoreEnabled == null || requirement == null) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }

        return new NormalizedFields(
                title,
                content,
                deadline,
                rewardAmount,
                teenyScoreEnabled,
                requirement);
    }

    private QuestVO findOwnedForUpdate(Long parentId, Long questId) {
        if (questId == null || questId <= 0) {
            throw new BusinessException(QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);
        }
        QuestVO quest = questMapper.selectByIdForUpdateByParent(questId, parentId);
        if (quest == null) {
            throw new BusinessException(QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);
        }
        return quest;
    }

    private String normalizeRequiredText(String value, int maxLength) {
        if (value == null) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
        return normalized;
    }

    private void requireLinkedChild(MemberPrincipal principal, Long childId) {
        try {
            familyAccessService.requireChildAccess(principal, childId);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == CommonErrorCode.AUTH_FORBIDDEN) {
                throw new BusinessException(QuestErrorCode.QUEST_CHILD_NOT_LINKED);
            }
            throw exception;
        }
    }

    private List<Long> resolveRetry(List<QuestVO> existing, NormalizedQuest request) {
        if (existing.size() != request.childIds().size()) {
            throw new BusinessException(QuestErrorCode.QUEST_CREATION_REQUEST_CONFLICT);
        }
        Map<Long, QuestVO> byChildId = new HashMap<>();
        for (QuestVO quest : existing) {
            if (byChildId.put(quest.getChildId(), quest) != null || !sameContent(quest, request)) {
                throw new BusinessException(QuestErrorCode.QUEST_CREATION_REQUEST_CONFLICT);
            }
        }

        List<Long> ids = new ArrayList<>(request.childIds().size());
        for (Long childId : request.childIds()) {
            QuestVO quest = byChildId.get(childId);
            if (quest == null) {
                throw new BusinessException(QuestErrorCode.QUEST_CREATION_REQUEST_CONFLICT);
            }
            ids.add(quest.getId());
        }
        return ids;
    }

    private boolean sameContent(QuestVO quest, NormalizedQuest request) {
        return Objects.equals(quest.getTitle(), request.title())
                && Objects.equals(quest.getContent(), request.content())
                && Objects.equals(quest.getDeadline(), request.deadline())
                && Objects.equals(quest.getRewardAmount(), request.rewardAmount())
                && Objects.equals(quest.getTeenyScoreEnabled(), request.teenyScoreEnabled())
                && quest.getVerificationRequirement() == request.verificationRequirement();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private record NormalizedQuest(
            List<Long> childIds,
            String title,
            String content,
            LocalDateTime deadline,
            Long rewardAmount,
            Boolean teenyScoreEnabled,
            VerificationRequirement verificationRequirement) {
    }

    private record NormalizedFields(
            String title,
            String content,
            LocalDateTime deadline,
            Long rewardAmount,
            Boolean teenyScoreEnabled,
            VerificationRequirement verificationRequirement) {
    }
}
