package com.teenyfin.teenymoney.global.sms;

public interface SmsSender {

    void sendVerificationCode(String phoneNumber, String code);
}
