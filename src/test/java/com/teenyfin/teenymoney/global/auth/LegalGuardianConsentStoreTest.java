package com.teenyfin.teenymoney.global.auth;

import com.teenyfin.teenymoney.domain.auth.service.LegalGuardianConsent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegalGuardianConsentStoreTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private LegalGuardianConsentStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new LegalGuardianConsentStore(redisTemplate, 600L);
    }

    @Test
    void saveAndFindPreserveLegalGuardianConsentForTenMinutes() {
        LegalGuardianConsent consent = consent();

        String token = store.save(consent);

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq("legal-guardian-consent:" + token), value.capture(), eq(Duration.ofMinutes(10)));
        when(valueOperations.get("legal-guardian-consent:" + token)).thenReturn(value.getValue());
        assertEquals(consent, store.find(token));
    }

    @Test
    void missingTokenReturnsNullAndDeleteInvalidatesToken() {
        assertNull(store.find("missing"));

        store.delete("used-token");

        verify(redisTemplate).delete("legal-guardian-consent:used-token");
    }

    private LegalGuardianConsent consent() {
        return new LegalGuardianConsent(
                "김보호", "01012345678", "MOTHER", "SMS_TEST",
                "verification-17", LocalDateTime.of(2026, 8, 4, 12, 0),
                "1.0", "1.0");
    }
}
