package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.dto.response.QuestDetailResponseDTO;
import com.teenyfin.teenymoney.domain.quest.dto.response.QuestListItemResponseDTO;
import com.teenyfin.teenymoney.domain.quest.dto.response.QuestListResponseDTO;
import com.teenyfin.teenymoney.domain.quest.dto.response.QuestVerificationResponseDTO;
import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.QuestTab;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVerificationVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import com.teenyfin.teenymoney.global.storage.S3Storage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestQueryService {

    private static final int PAGE_SIZE = 20;
    private static final int FETCH_SIZE = PAGE_SIZE + 1;
    private static final long IMAGE_RETENTION_DAYS = 90;

    private final QuestMapper questMapper;
    private final S3Storage s3Storage;
    private final Clock clock;

    @Transactional(readOnly = true)
    public QuestListResponseDTO getQuests(
            MemberPrincipal principal,
            QuestTab tab,
            Long childId,
            String encodedCursor) {
        if (tab == null) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }

        Cursor cursor = decode(encodedCursor, tab);
        List<QuestVO> rows;
        if (isParent(principal)) {
            rows = questMapper.selectPageByParent(
                    principal.memberId(), childId, tab.statuses(),
                    cursor == null ? null : cursor.time(),
                    cursor == null ? null : cursor.questId(),
                    tab.isCompleted(), FETCH_SIZE);
        } else if (isChild(principal)) {
            if (childId != null) {
                throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
            }
            rows = questMapper.selectPageByChild(
                    principal.memberId(), tab.statuses(),
                    cursor == null ? null : cursor.time(),
                    cursor == null ? null : cursor.questId(),
                    tab.isCompleted(), FETCH_SIZE);
        } else {
            throw new BusinessException(CommonErrorCode.AUTH_FORBIDDEN);
        }

        boolean hasNext = rows.size() > PAGE_SIZE;
        List<QuestVO> page = hasNext ? rows.subList(0, PAGE_SIZE) : rows;
        List<QuestListItemResponseDTO> items = page.stream()
                .map(quest -> QuestListItemResponseDTO.of(
                        quest, s3Storage.presignedUrl(quest.getChildProfileImageKey())))
                .toList();
        String nextCursor = hasNext ? encode(tab, page.get(PAGE_SIZE - 1)) : null;
        return new QuestListResponseDTO(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public QuestDetailResponseDTO getQuest(MemberPrincipal principal, Long questId) {
        QuestVO quest;
        if (isParent(principal)) {
            quest = questMapper.selectDetailByParent(questId, principal.memberId());
        } else if (isChild(principal)) {
            quest = questMapper.selectDetailByChild(questId, principal.memberId());
        } else {
            throw new BusinessException(CommonErrorCode.AUTH_FORBIDDEN);
        }
        if (quest == null) {
            throw new BusinessException(QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED);
        }

        QuestVerificationResponseDTO latestVerification = latestVerification(questId);
        return QuestDetailResponseDTO.of(
                quest,
                s3Storage.presignedUrl(quest.getChildProfileImageKey()),
                latestVerification);
    }

    private QuestVerificationResponseDTO latestVerification(Long questId) {
        QuestVerificationVO verification = questMapper.selectLatestVerification(questId);
        if (verification == null) {
            return null;
        }

        boolean hasImage = verification.getImageKey() != null
                && !verification.getImageKey().isBlank();
        boolean imageExpired = hasImage
                && verification.getCreatedAt() != null
                && !LocalDateTime.now(clock).withNano(0)
                .isBefore(verification.getCreatedAt().plusDays(IMAGE_RETENTION_DAYS).withNano(0));
        String imageUrl = imageExpired ? null : s3Storage.presignedUrl(verification.getImageKey());
        return QuestVerificationResponseDTO.of(verification, imageUrl, imageExpired);
    }

    private String encode(QuestTab tab, QuestVO quest) {
        LocalDateTime time = tab.isCompleted() ? quest.getEndedAt() : quest.getDeadline();
        String value = tab.name() + "|" + time + "|" + quest.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decode(String encodedCursor, QuestTab requestedTab) {
        if (encodedCursor == null || encodedCursor.isBlank()) {
            return null;
        }
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(encodedCursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", -1);
            if (parts.length != 3 || QuestTab.valueOf(parts[0]) != requestedTab) {
                throw new IllegalArgumentException();
            }
            return new Cursor(
                    requestedTab,
                    LocalDateTime.parse(parts[1]),
                    Long.parseLong(parts[2]));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }

    private boolean isParent(MemberPrincipal principal) {
        return principal != null && "PARENT".equals(principal.role());
    }

    private boolean isChild(MemberPrincipal principal) {
        return principal != null && "CHILD".equals(principal.role());
    }

    private record Cursor(QuestTab tab, LocalDateTime time, Long questId) {
    }
}
