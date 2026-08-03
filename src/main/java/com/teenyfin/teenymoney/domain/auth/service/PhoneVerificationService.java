package com.teenyfin.teenymoney.domain.auth.service;

import com.teenyfin.teenymoney.domain.auth.exception.AuthErrorCode;
import com.teenyfin.teenymoney.global.auth.PhoneVerificationStore;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.sms.SmsDeliveryException;
import com.teenyfin.teenymoney.global.sms.SmsSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class PhoneVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PhoneVerificationStore store;
    private final SmsSender smsSender;
    private final boolean testMode;
    private final String testCode;
    private final int maxAttempts;

    public PhoneVerificationService(
            PhoneVerificationStore store,
            SmsSender smsSender,
            @Value("${sms.test-mode}") boolean testMode,
            @Value("${sms.test-code}") String testCode,
            @Value("${sms.max-attempts}") int maxAttempts) {
        if (testMode && !testCode.matches("\\d{6}")) {
            throw new IllegalStateException("SMS_TEST_CODE must be exactly six digits in test mode");
        }
        this.store = store;
        this.smsSender = smsSender;
        this.testMode = testMode;
        this.testCode = testCode;
        this.maxAttempts = maxAttempts;
    }

    public void sendCode(String rawPhoneNumber) {
        String phoneNumber = normalizePhoneNumber(rawPhoneNumber);
        if (store.isCooldownActive(phoneNumber)) {
            throw new BusinessException(AuthErrorCode.AUTH_SMS_TOO_MANY_REQUESTS);
        }

        String code = testMode ? testCode : generateCode();
        try {
            smsSender.sendVerificationCode(phoneNumber, code);
        } catch (SmsDeliveryException exception) {
            throw new BusinessException(AuthErrorCode.AUTH_SMS_UNAVAILABLE);
        }

        store.saveCode(phoneNumber, code);
        store.startCooldown(phoneNumber);
        store.resetAttempts(phoneNumber);
    }

    public void verify(String rawPhoneNumber, String submittedCode) {
        String phoneNumber = normalizePhoneNumber(rawPhoneNumber);
        String storedCode = store.findCode(phoneNumber);
        if (storedCode == null) {
            throw new BusinessException(AuthErrorCode.AUTH_VERIFICATION_CODE_EXPIRED);
        }
        if (store.getAttempts(phoneNumber) >= maxAttempts) {
            store.deleteCode(phoneNumber);
            throw new BusinessException(AuthErrorCode.AUTH_VERIFICATION_TOO_MANY_ATTEMPTS);
        }
        if (!storedCode.equals(submittedCode)) {
            Long attempts = store.incrementAttempts(phoneNumber);
            if (attempts == null || attempts >= maxAttempts) {
                store.deleteCode(phoneNumber);
                throw new BusinessException(AuthErrorCode.AUTH_VERIFICATION_TOO_MANY_ATTEMPTS);
            }
            throw new BusinessException(AuthErrorCode.AUTH_VERIFICATION_CODE_INVALID);
        }
    }

    public void consume(String rawPhoneNumber) {
        store.deleteAll(normalizePhoneNumber(rawPhoneNumber));
    }

    private String normalizePhoneNumber(String rawPhoneNumber) {
        return rawPhoneNumber.replaceAll("\\D", "");
    }

    private String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }
}
