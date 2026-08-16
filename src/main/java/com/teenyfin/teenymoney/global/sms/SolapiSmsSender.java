package com.teenyfin.teenymoney.global.sms;

import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.dto.response.MultipleDetailMessageSentResponse;
import com.solapi.sdk.message.model.FailedMessage;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.model.group.GroupInfo;
import com.solapi.sdk.message.service.DefaultMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SmsSender의 유일한 구현체. 테스트 모드는 별도 클래스가 아니라 여기서 분기한다.
 * 구현체가 하나뿐이면 빈 선택 설정(SmsConfig)도, 널 오브젝트 구현체도 필요 없다.
 */
@Component
public class SolapiSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SolapiSmsSender.class);

    // 테스트 모드에서는 null이다. 이 필드가 곧 "실제 발송 여부" 플래그다.
    private final DefaultMessageService messageService;
    private final String senderNumber;

    public SolapiSmsSender(
            @Value("${sms.test-mode}") boolean testMode,
            @Value("${solapi.api-key}") String apiKey,
            @Value("${solapi.api-secret}") String apiSecret,
            @Value("${solapi.sender-number}") String senderNumber) {
        // 콘솔에 하이픈 없이 등록되므로 숫자만 남긴다.
        this.senderNumber = senderNumber.replaceAll("\\D", "");

        if (testMode) {
            this.messageService = null;
            // 기동 로그에 한 번은 반드시 남긴다. 이게 없으면 "왜 문자가 안 오지"를
            // 코드까지 뒤져야 알 수 있다. 실제로 그렇게 한 번 잃었다.
            log.warn("SMS test mode is ON (sms.test-mode=true). No real SMS will be sent.");
            return;
        }

        // 발송 시점이 아니라 기동 시점에 깨뜨린다. 설정 누락을 배포 직후에 알 수 있다.
        if (apiKey.isEmpty() || apiSecret.isEmpty() || this.senderNumber.isEmpty()) {
            throw new IllegalStateException(
                    "SOLAPI_API_KEY, SOLAPI_API_SECRET, SOLAPI_SENDER_NUMBER are required when SMS_TEST_MODE=false");
        }
        this.messageService = SolapiClient.INSTANCE.createInstance(apiKey, apiSecret);
        log.info("SMS live mode. Solapi sender number={}", this.senderNumber);
    }

    @Override
    public void sendVerificationCode(String phoneNumber, String code) {
        if (messageService == null) {
            return;
        }

        Message message = new Message();
        message.setFrom(senderNumber);
        message.setTo(phoneNumber);
        // 90바이트(EUC-KR)를 넘으면 LMS로 자동 전환되어 단가가 오른다. 이 문구는 약 37바이트다.
        message.setText("[TeenyMoney] 인증번호는 " + code + "입니다.");

        MultipleDetailMessageSentResponse response;
        try {
            response = messageService.send(message);
        } catch (Exception exception) {
            // SDK 예외는 셋 다 checked Exception이고 공통 조상이 인터페이스라 catch로 못 묶는다.
            // 호출부가 SmsDeliveryException을 BusinessException으로 바꿔 삼키므로 여기서 남겨야 한다.
            log.error("Solapi send failed", exception);
            throw new SmsDeliveryException("SMS delivery failed", exception);
        }

        // HTTP 200이어도 개별 메시지가 거절될 수 있다(발신번호 미등록, 잔액 부족 등).
        // 이걸 안 보면 "성공했는데 문자는 안 오는" 상태가 된다.
        List<FailedMessage> failures = response.getFailedMessageList();
        if (failures != null && !failures.isEmpty()) {
            FailedMessage failure = failures.get(0);
            log.error("Solapi rejected the message: statusCode={} statusMessage={}",
                    failure.getStatusCode(), failure.getStatusMessage());
            throw new SmsDeliveryException(
                    "SMS rejected by Solapi: " + failure.getStatusCode() + " " + failure.getStatusMessage());
        }

        // 수신번호는 개인정보라 남기지 않는다. groupId만 있으면 Solapi 콘솔에서 대조된다.
        GroupInfo groupInfo = response.getGroupInfo();
        log.info("Solapi accepted the message. groupId={}",
                groupInfo == null ? "unknown" : groupInfo.getGroupId());
    }
}
