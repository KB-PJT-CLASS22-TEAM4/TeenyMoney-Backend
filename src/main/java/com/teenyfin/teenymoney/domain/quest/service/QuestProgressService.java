package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.dto.request.QuestDeclineRequestDTO;
import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.mapper.QuestMapper;
import com.teenyfin.teenymoney.domain.quest.vo.DeclineReasonCode;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class QuestProgressService {

    // 잠금 조회, UPDATE
    private final QuestMapper questMapper;
    // 상태, 기한 검사
    private final QuestStatePolicy questStatePolicy;
    // now()사용 목적
    private final Clock clock;

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

    /**
     * 공백만 있는 값은 없는 것으로 본다. DB에 "   "가 들어가면 설명이 있는 것처럼 보인다.
     */
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
}

