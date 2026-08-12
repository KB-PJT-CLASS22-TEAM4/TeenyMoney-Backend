package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class QuestStatePolicy {

    public void requireAvailableBeforeDeadline(QuestVO quest, LocalDateTime now) {
        requireStatusBeforeDeadline(quest, QuestStatus.AVAILABLE, now);
    }

    public void requireStatusBeforeDeadline(QuestVO quest, QuestStatus expected, LocalDateTime now) {

        // 현재 quest 상태가 예상하는 상태와 다르다면
        if (quest.getStatus() != expected) {
            throw new BusinessException(QuestErrorCode.QUEST_STATUS_CONFLICT);
        }

        // 이미 퀘스트의 유효기간이 종료되었다면
        if (now.isAfter(quest.getDeadline())) {
            throw new BusinessException(QuestErrorCode.QUEST_DEADLINE_PASSED);
        }
    }
}
