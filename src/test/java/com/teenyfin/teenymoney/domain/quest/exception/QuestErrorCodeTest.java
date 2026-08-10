package com.teenyfin.teenymoney.domain.quest.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class QuestErrorCodeTest {

    @Test
    void 퀘스트_오류는_상황에_맞는_HTTP_상태를_사용한다() {
        assertThat(QuestErrorCode.QUEST_REWARD_INVALID.getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(QuestErrorCode.QUEST_PARENT_ONLY.getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(QuestErrorCode.QUEST_NOT_FOUND_OR_ACCESS_DENIED.getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(QuestErrorCode.QUEST_DEADLINE_PASSED.getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void 오류_코드는_enum_이름과_같다() {
        assertThat(QuestErrorCode.QUEST_CREATION_REQUEST_CONFLICT.getCode())
                .isEqualTo("QUEST_CREATION_REQUEST_CONFLICT");
    }
}
