package com.teenyfin.teenymoney.global.sms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SolapiSmsSenderTest {

    @Test
    void testModeSkipsExternalProvider() {
        SolapiSmsSender sender = new SolapiSmsSender(true, "", "", "");

        assertDoesNotThrow(() -> sender.sendVerificationCode("01066364241", "123456"));
    }

    @Test
    void normalModeFailsAtStartupWhenCredentialsAreMissing() {
        assertThrows(IllegalStateException.class,
                () -> new SolapiSmsSender(false, "", "", "01066364241"));
        assertThrows(IllegalStateException.class,
                () -> new SolapiSmsSender(false, "key", "secret", ""));
    }
}
