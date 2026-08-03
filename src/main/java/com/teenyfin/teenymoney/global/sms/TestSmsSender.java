package com.teenyfin.teenymoney.global.sms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TestSmsSender implements SmsSender {

    private final boolean testMode;

    public TestSmsSender(@Value("${sms.test-mode}") boolean testMode) {
        this.testMode = testMode;
    }

    @Override
    public void sendVerificationCode(String phoneNumber, String code) {
        if (!testMode) {
            throw new SmsDeliveryException("SMS provider is not configured");
        }
    }
}
