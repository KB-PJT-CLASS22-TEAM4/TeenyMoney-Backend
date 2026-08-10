package com.teenyfin.teenymoney.domain.quest.vo;

import java.util.List;

public enum QuestTab {
    AVAILABLE(List.of(QuestStatus.AVAILABLE), false),
    ONGOING(List.of(QuestStatus.IN_PROGRESS, QuestStatus.PENDING), false),
    COMPLETED(List.of(
            QuestStatus.COMPLETED,
            QuestStatus.FAILED,
            QuestStatus.EXPIRED,
            QuestStatus.DECLINED), true);

    private final List<QuestStatus> statuses;
    private final boolean completed;

    QuestTab(List<QuestStatus> statuses, boolean completed) {
        this.statuses = statuses;
        this.completed = completed;
    }

    public List<QuestStatus> statuses() {
        return statuses;
    }

    public boolean isCompleted() {
        return completed;
    }
}
