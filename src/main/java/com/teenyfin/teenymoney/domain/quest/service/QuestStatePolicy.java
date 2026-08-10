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
        if (quest.getStatus() != QuestStatus.AVAILABLE) {
            throw new BusinessException(QuestErrorCode.QUEST_STATUS_CONFLICT);
        }
        if (now.isAfter(quest.getDeadline())) {
            throw new BusinessException(QuestErrorCode.QUEST_DEADLINE_PASSED);
        }
    }
}
