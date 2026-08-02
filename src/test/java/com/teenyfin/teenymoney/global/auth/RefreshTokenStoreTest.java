package com.teenyfin.teenymoney.global.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
