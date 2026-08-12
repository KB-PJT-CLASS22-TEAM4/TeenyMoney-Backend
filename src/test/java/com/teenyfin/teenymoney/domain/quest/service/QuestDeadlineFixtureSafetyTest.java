package com.teenyfin.teenymoney.domain.quest.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class QuestDeadlineFixtureSafetyTest {

    @Test
    @DisplayName("마감 통합 테스트는 기존 seed 회원을 수정하지 않고 전용 회원만 사용한다")
    void usesDedicatedMembersWithoutChangingSeedMembers() throws IOException {
        String setup = resource("quest/setup-quest-deadline-test.sql");
        String cleanup = resource("quest/cleanup-quest-deadline-test.sql");

        assertThat(setup)
                .contains("INSERT INTO `T_MBR_INFO_M`")
                .contains("-900001", "-900002")
                .doesNotContain("UPDATE `T_MBR_INFO_M`");
        assertThat(cleanup)
                .contains("DELETE FROM `T_MBR_INFO_M`")
                .contains("-900001", "-900002")
                .doesNotContain("UPDATE `T_MBR_INFO_M`");
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("classpath resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
