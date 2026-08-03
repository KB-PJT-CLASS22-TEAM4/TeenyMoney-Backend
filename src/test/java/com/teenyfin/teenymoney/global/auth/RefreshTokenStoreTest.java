package com.teenyfin.teenymoney.global.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenStoreTest {

    private static final long REFRESH_EXPIRATION_MS = 1_209_600_000L;
    private static final Long MEMBER_ID = 17L;

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RefreshTokenStore refreshTokenStore;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        refreshTokenStore = new RefreshTokenStore(
                redisTemplate,
                REFRESH_EXPIRATION_MS);
    }

    @Test
    void saveStoresTokenUnderMemberKeyWithRefreshExpiration() {
        refreshTokenStore.save(MEMBER_ID, "refresh-token");

        verify(valueOperations).set(
                "refresh:17",
                "refresh-token",
                Duration.ofMillis(REFRESH_EXPIRATION_MS));
    }

    @Test
    void findReturnsTokenStoredUnderMemberKey() {
        when(valueOperations.get("refresh:17"))
                .thenReturn("stored-refresh-token");

        String token = refreshTokenStore.find(MEMBER_ID);

        assertEquals("stored-refresh-token", token);
    }

    @Test
    void deleteRemovesTokenStoredUnderMemberKey() {
        refreshTokenStore.delete(MEMBER_ID);

        verify(redisTemplate).delete("refresh:17");
    }

    @Test
    void getOrCreateGenerationReturnsAtomicScriptResult() {
        when(redisTemplate.execute(
                any(),
                eq(List.of("auth:generation:17")),
                anyString(),
                eq(String.valueOf(REFRESH_EXPIRATION_MS))))
                .thenReturn("generation-17");

        assertEquals("generation-17", refreshTokenStore.getOrCreateGeneration(MEMBER_ID));
    }

    @Test
    void findGenerationReturnsCurrentAccountGeneration() {
        when(valueOperations.get("auth:generation:17")).thenReturn("generation-17");

        assertEquals("generation-17", refreshTokenStore.findGeneration(MEMBER_ID));
    }

    @Test
    void rotateReturnsTrueOnlyWhenRedisAtomicallyReplacesCurrentToken() {
        when(redisTemplate.execute(
                any(),
                eq(List.of("refresh:17", "auth:generation:17")),
                eq("old-refresh"),
                eq("new-refresh"),
                eq("generation-17"),
                eq(String.valueOf(REFRESH_EXPIRATION_MS))))
                .thenReturn(1L);

        assertTrue(refreshTokenStore.rotate(
                MEMBER_ID, "old-refresh", "new-refresh", "generation-17"));
    }

    @Test
    void revokeAllRemovesRefreshAndGenerationKeysForOnlyThatMember() {
        refreshTokenStore.revokeAll(MEMBER_ID);

        verify(redisTemplate).delete(List.of("refresh:17", "auth:generation:17"));
    }
}
