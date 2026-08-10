package com.teenyfin.teenymoney.domain.quest.service;

import com.teenyfin.teenymoney.domain.quest.exception.QuestErrorCode;
import com.teenyfin.teenymoney.domain.quest.vo.QuestStatus;
import com.teenyfin.teenymoney.domain.quest.vo.QuestVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestStatePolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 10, 0);
    private final QuestStatePolicy policy = new QuestStatePolicy();

    @Test
    void 서버_시각과_기한이_같은_초면_AVAILABLE_명령을_허용한다() {
        assertThatCode(() -> policy.requireAvailableBeforeDeadline(
                quest(QuestStatus.AVAILABLE, NOW), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void AVAILABLE이_아니면_상태_충돌이다() {
        assertError(
                () -> policy.requireAvailableBeforeDeadline(
                        quest(QuestStatus.IN_PROGRESS, NOW.plusDays(1)), NOW),
                QuestErrorCode.QUEST_STATUS_CONFLICT);
    }

    @Test
    void 서버_시각이_기한보다_늦으면_기한_경과다() {
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
