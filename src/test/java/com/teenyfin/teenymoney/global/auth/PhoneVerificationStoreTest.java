package com.teenyfin.teenymoney.global.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhoneVerificationStoreTest {

    private static final String PHONE_NUMBER = "01012345678";

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PhoneVerificationStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new PhoneVerificationStore(redisTemplate, 180L, 60L);
    }

    @Test
    void saveCodeStoresItWithVerificationTtl() {
        store.saveCode(PHONE_NUMBER, "123456");

        verify(valueOperations).set(
                "phone-verification:code:01012345678",
                "123456",
                Duration.ofSeconds(180));
    }

    @Test
    void findCodeReturnsStoredCode() {
        when(valueOperations.get("phone-verification:code:01012345678"))
                .thenReturn("123456");

        assertEquals("123456", store.findCode(PHONE_NUMBER));
    }

    @Test
    void cooldownReflectsRedisKeyAndStartsWithCooldownTtl() {
        when(redisTemplate.hasKey("phone-verification:cooldown:01012345678"))
                .thenReturn(true, false);

        assertTrue(store.isCooldownActive(PHONE_NUMBER));
        assertFalse(store.isCooldownActive(PHONE_NUMBER));

        store.startCooldown(PHONE_NUMBER);
        verify(valueOperations).set(
                "phone-verification:cooldown:01012345678",
                "1",
                Duration.ofSeconds(60));
    }

    @Test
    void attemptsAreParsedAndMissingValueMeansZero() {
        when(valueOperations.get("phone-verification:attempts:01012345678"))
                .thenReturn("4")
                .thenReturn((String) null);

        assertEquals(4L, store.getAttempts(PHONE_NUMBER));
        assertEquals(0L, store.getAttempts(PHONE_NUMBER));
    }

    @Test
    void incrementAttemptsKeepsAttemptStateForVerificationTtl() {
        when(valueOperations.increment("phone-verification:attempts:01012345678"))
                .thenReturn(3L);

        assertEquals(3L, store.incrementAttempts(PHONE_NUMBER));
        verify(redisTemplate).expire(
                "phone-verification:attempts:01012345678",
                Duration.ofSeconds(180));
    }

    @Test
    void incrementAttemptsPreservesMissingRedisResult() {
        when(valueOperations.increment("phone-verification:attempts:01012345678"))
                .thenReturn(null);

        assertNull(store.incrementAttempts(PHONE_NUMBER));
    }

    @Test
    void resetAttemptsDeletesOnlyAttemptState() {
        store.resetAttempts(PHONE_NUMBER);

        verify(redisTemplate).delete("phone-verification:attempts:01012345678");
    }

    @Test
    void deleteCodeInvalidatesOnlyTheIssuedCode() {
        store.deleteCode(PHONE_NUMBER);

        verify(redisTemplate).delete("phone-verification:code:01012345678");
    }

    @Test
    void deleteAllRemovesCodeCooldownAndAttempts() {
        store.deleteAll(PHONE_NUMBER);

        verify(redisTemplate).delete(List.of(
                "phone-verification:code:01012345678",
                "phone-verification:cooldown:01012345678",
                "phone-verification:attempts:01012345678"));
    }
}
