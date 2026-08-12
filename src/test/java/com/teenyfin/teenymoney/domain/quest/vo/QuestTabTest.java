package com.teenyfin.teenymoney.domain.quest.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestTabTest {

    @Test
    @DisplayName("탭은 조회할 상태와 정렬 기준을 정한다")
    void tabDefinesStatusesAndSortBasis() {
        assertThat(QuestTab.AVAILABLE.statuses())
                .containsExactly(QuestStatus.AVAILABLE);
        assertThat(QuestTab.ONGOING.statuses())
                .containsExactly(QuestStatus.IN_PROGRESS, QuestStatus.PENDING);
        assertThat(QuestTab.COMPLETED.statuses())
                .containsExactly(
                        QuestStatus.COMPLETED,
                        QuestStatus.FAILED,
                        QuestStatus.EXPIRED,
                        QuestStatus.DECLINED);

        assertThat(QuestTab.AVAILABLE.isCompleted()).isFalse();
        assertThat(QuestTab.ONGOING.isCompleted()).isFalse();
        assertThat(QuestTab.COMPLETED.isCompleted()).isTrue();
    }
}
