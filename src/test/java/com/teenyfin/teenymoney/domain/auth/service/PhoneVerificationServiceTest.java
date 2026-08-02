package com.teenyfin.teenymoney.domain.auth.service;

import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.global.auth.PhoneVerificationStore;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.sms.SmsDeliveryException;
import com.teenyfin.teenymoney.global.sms.SmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhoneVerificationServiceTest {

    private PhoneVerificationStore store;
    private SmsSender smsSender;
    private PhoneVerificationService service;

    @BeforeEach
    void setUp() {
        store = mock(PhoneVerificationStore.class);
        smsSender = mock(SmsSender.class);
        service = new PhoneVerificationService(store, smsSender, true, "123456", 5);
    }

    @Test
    void testModeSendsFixedCodeAndStoresNormalizedPhoneState() {
        service.sendCode("010-1234-5678");

        verify(smsSender).sendVerificationCode("01012345678", "123456");
        verify(store).saveCode("01012345678", "123456");
        verify(store).startCooldown("01012345678");
        verify(store).resetAttempts("01012345678");
    }

    @Test
    void activeCooldownRejectsResendBeforeSmsDelivery() {
        when(store.isCooldownActive("01012345678")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.sendCode("010-1234-5678"));

        assertEquals(AuthErrorCode.AUTH_SMS_TOO_MANY_REQUESTS, exception.getErrorCode());
        verify(smsSender, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void normalModeGeneratesSixDigitCode() {
        PhoneVerificationService normalService =
                new PhoneVerificationService(store, smsSender, false, "", 5);

        normalService.sendCode("01012345678");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsSender).sendVerificationCode(eq("01012345678"), codeCaptor.capture());
        assertTrue(codeCaptor.getValue().matches("\\d{6}"));
        verify(store).saveCode("01012345678", codeCaptor.getValue());
    }

    @Test
    void invalidTestCodeIsRejectedAtConstruction() {
        assertThrows(IllegalStateException.class,
                () -> new PhoneVerificationService(store, smsSender, true, "12345", 5));
    }

    @Test
    void smsFailureDoesNotCreateVerificationState() {
        org.mockito.Mockito.doThrow(new SmsDeliveryException("provider unavailable"))
                .when(smsSender).sendVerificationCode("01012345678", "123456");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.sendCode("01012345678"));

        assertEquals(AuthErrorCode.AUTH_SMS_UNAVAILABLE, exception.getErrorCode());
        verify(store, never()).saveCode(anyString(), anyString());
        verify(store, never()).startCooldown(anyString());
    }

    @Test
    void missingCodeIsExpired() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.verify("010-1234-5678", "123456"));

        assertEquals(AuthErrorCode.AUTH_VERIFICATION_CODE_EXPIRED, exception.getErrorCode());
    }

    @Test
    void wrongCodeIncrementsAttemptsAndIsInvalid() {
        when(store.findCode("01012345678")).thenReturn("123456");
        when(store.incrementAttempts("01012345678")).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.verify("01012345678", "999999"));

        assertEquals(AuthErrorCode.AUTH_VERIFICATION_CODE_INVALID, exception.getErrorCode());
    }

    @Test
    void fifthWrongCodeReachesAttemptLimit() {
        when(store.findCode("01012345678")).thenReturn("123456");
        when(store.getAttempts("01012345678")).thenReturn(4L);
        when(store.incrementAttempts("01012345678")).thenReturn(5L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.verify("01012345678", "999999"));

        assertEquals(AuthErrorCode.AUTH_VERIFICATION_TOO_MANY_ATTEMPTS,
                exception.getErrorCode());
        verify(store).deleteCode("01012345678");
    }

    @Test
    void attemptLimitRejectsEvenCorrectCode() {
        when(store.findCode("01012345678")).thenReturn("123456");
        when(store.getAttempts("01012345678")).thenReturn(5L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.verify("01012345678", "123456"));

        assertEquals(AuthErrorCode.AUTH_VERIFICATION_TOO_MANY_ATTEMPTS,
                exception.getErrorCode());
        verify(store).deleteCode("01012345678");
    }

    @Test
    void correctCodeDoesNotConsumeStateBeforeMemberInsert() {
        when(store.findCode("01012345678")).thenReturn("123456");

        service.verify("010-1234-5678", "123456");

        verify(store, never()).deleteAll(anyString());
    }

    @Test
    void consumeDeletesAllNormalizedPhoneState() {
        service.consume("010-1234-5678");

        verify(store).deleteAll("01012345678");
    }
}
