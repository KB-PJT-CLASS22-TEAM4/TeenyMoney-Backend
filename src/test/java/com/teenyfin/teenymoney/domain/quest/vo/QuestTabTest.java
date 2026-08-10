package com.teenyfin.teenymoney.domain.quest.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestTabTest {

    @Test
    void 탭은_조회할_상태와_정렬_기준을_정한다() {
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
