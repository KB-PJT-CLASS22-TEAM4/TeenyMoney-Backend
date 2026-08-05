package com.teenyfin.teenymoney.domain.auth.service;

import com.teenyfin.teenymoney.global.auth.LegalGuardianConsentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegalGuardianVerificationServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private PhoneVerificationService phoneVerificationService;
    private LegalGuardianConsentStore store;
    private LegalGuardianVerificationService service;

    @BeforeEach
    void setUp() {
        phoneVerificationService = mock(PhoneVerificationService.class);
        store = mock(LegalGuardianConsentStore.class);
        service = new LegalGuardianVerificationService(phoneVerificationService, store, CLOCK);
    }

    @Test
    void confirmCreatesTokenFromVerifiedNormalizedLegalGuardianInformation() {
        when(store.save(org.mockito.ArgumentMatchers.any())).thenReturn("legal-guardian-token");

        String token = service.confirm(
                "김보호", "MOTHER", "010-1234-5678", "123456", "1.0", "1.0");

        assertEquals("legal-guardian-token", token);
        ArgumentCaptor<LegalGuardianConsent> consent = ArgumentCaptor.forClass(LegalGuardianConsent.class);
        verify(store).save(consent.capture());
        assertEquals("김보호", consent.getValue().name());
        assertEquals("01012345678", consent.getValue().phoneNumber());
        assertEquals("MOTHER", consent.getValue().relationship());
        assertEquals("SMS_TEST", consent.getValue().verificationMethod());
        assertEquals(LocalDateTime.of(2026, 8, 4, 12, 0), consent.getValue().verifiedAt());

        var order = inOrder(phoneVerificationService, store);
        order.verify(phoneVerificationService).verify("01012345678", "123456");
        order.verify(store).save(org.mockito.ArgumentMatchers.any());
        order.verify(phoneVerificationService).consume("01012345678");
    }
}
