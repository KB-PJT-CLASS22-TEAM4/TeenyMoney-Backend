package com.teenyfin.teenymoney.domain.quest.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class QuestErrorCodeTest {

    @Test
    @DisplayName("퀘스트 오류는 상황에 맞는 HTTP 상태를 사용한다")
    void usesHttpStatusMatchingEachSituation() {
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
    @DisplayName("오류 코드는 enum 이름과 같다")
    void codeEqualsEnumName() {
        assertThat(QuestErrorCode.QUEST_CREATION_REQUEST_CONFLICT.getCode())
                .isEqualTo("QUEST_CREATION_REQUEST_CONFLICT");
    }
}
