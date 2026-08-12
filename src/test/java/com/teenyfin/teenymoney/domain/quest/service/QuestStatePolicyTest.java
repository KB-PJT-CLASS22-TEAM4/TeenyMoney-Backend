package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestStatePolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 10, 0);
    private final QuestStatePolicy policy = new QuestStatePolicy();

    @Test
    @DisplayName("서버 시각과 기한이 같은 초면 AVAILABLE 명령을 허용한다")
    void allowsAvailableCommandOnExactDeadlineSecond() {
        assertThatCode(() -> policy.requireAvailableBeforeDeadline(
                quest(QuestStatus.AVAILABLE, NOW), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AVAILABLE이 아니면 상태 충돌이다")
    void nonAvailableStatusIsConflict() {
        assertError(
                () -> policy.requireAvailableBeforeDeadline(
                        quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1)), NOW),
                QuestErrorCode.QUEST_STATUS_CONFLICT);
    }

    @Test
    @DisplayName("서버 시각이 기한보다 늦으면 기한 경과다")
    void serverTimeAfterDeadlineIsDeadlinePassed() {
        assertError(
                () -> policy.requireAvailableBeforeDeadline(
                        quest(QuestStatus.AVAILABLE, NOW.minusSeconds(1)), NOW),
                QuestErrorCode.QUEST_DEADLINE_PASSED);
    }

    private QuestVO quest(QuestStatus status, LocalDateTime deadline) {
        return QuestVO.builder().status(status).deadline(deadline).build();
    }

    private void assertError(Runnable call, QuestErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
