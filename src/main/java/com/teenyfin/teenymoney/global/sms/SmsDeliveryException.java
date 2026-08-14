package com.teenyfin.teenymoney.global.sms;

public class SmsDeliveryException extends RuntimeException {

    public SmsDeliveryException(String message) {
        super(message);
    }

    // 원인을 붙들어둔다. 이걸 버리면 Solapi 응답 실패가 스택트레이스 없이 사라진다.
    public SmsDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
