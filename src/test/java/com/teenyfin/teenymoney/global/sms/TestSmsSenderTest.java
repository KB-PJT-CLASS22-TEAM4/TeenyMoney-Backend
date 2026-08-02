package com.teenyfin.teenymoney.global.sms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSmsSenderTest {

    @Test
    void testModeAcceptsDeliveryWithoutExternalProvider() {
        TestSmsSender sender = new TestSmsSender(true);

        assertDoesNotThrow(() -> sender.sendVerificationCode("01012345678", "123456"));
    }

    @Test
    void normalModeFailsClosedUntilProviderIsConfigured() {
        TestSmsSender sender = new TestSmsSender(false);

        assertThrows(SmsDeliveryException.class,
                () -> sender.sendVerificationCode("01012345678", "123456"));
    }
}
