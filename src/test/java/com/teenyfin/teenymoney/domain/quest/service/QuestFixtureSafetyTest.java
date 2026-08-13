package com.teenyfin.teenymoney.domain.quest.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 퀘스트 DB 통합 테스트의 픽스처가 공용 DB 의 기존 회원을 건드리지 않는지 본다.
 *
 * 이 검사는 DB 없이 돌아간다. 실제 통합 테스트는 로컬 연결일 때만 실행되어 CI 에서는
 * 건너뛰므로, 위험한 픽스처가 들어와도 아무도 모르는 채 병합될 수 있기 때문이다.
 */
class QuestFixtureSafetyTest {

    @Test
    @DisplayName("마감 배치 픽스처는 기존 seed 회원을 수정하지 않고 전용 회원만 사용한다")
    void deadlineFixtureUsesDedicatedMembers() throws IOException {
        assertDedicatedMembers(
                "quest/setup-quest-deadline-test.sql",
                "quest/cleanup-quest-deadline-test.sql",
                "-900001", "-900002");
    }

    @Test
    @DisplayName("전체 흐름 픽스처는 기존 seed 회원을 수정하지 않고 전용 회원만 사용한다")
    void flowFixtureUsesDedicatedMembers() throws IOException {
        assertDedicatedMembers(
                "quest/setup-quest-flow-test.sql",
                "quest/cleanup-quest-flow-test.sql",
                "900011", "900012");
    }

    private void assertDedicatedMembers(
            String setupPath, String cleanupPath, String parentId, String childId)
            throws IOException {
        assertThat(resource(setupPath))
                .contains("INSERT INTO `T_MBR_INFO_M`")
                .contains(parentId, childId)
                .doesNotContain("UPDATE `T_MBR_INFO_M`");
        assertThat(resource(cleanupPath))
                .contains("DELETE FROM `T_MBR_INFO_M`")
                .contains(parentId, childId)
                .doesNotContain("UPDATE `T_MBR_INFO_M`");
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("classpath resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
