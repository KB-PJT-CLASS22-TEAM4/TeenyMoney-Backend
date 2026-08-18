package com.teenyfin.teenymoney.domain.notification.service;

import com.google.firebase.messaging.*;
import com.teenyfin.teenymoney.domain.notification.dto.NotificationMessage;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationVO;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    public void send(Message message) {
        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM 전송 성공: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("FCM 전송 실패: {}", e.getMessage(), e);
        }
    }

    //다음 행동 유도가 필요할 때 path를 쓴다
    public Message createMessage(String deviceToken, NotificationMessage notificationMessage, NotificationVO notificationVO) {
        Notification notification = notificationMessage.toNotification();

        Message.Builder messageBuilder = Message.builder()
                .setToken(deviceToken)
                .setNotification(notification)
                .setWebpushConfig(WebpushConfig.builder()
                        .putHeader("TTL", "600")
                        .build())
                .putData("title", notificationVO.getTitle())
                .putData("content", notificationVO.getContent());

        // referenceType이 존재할 경우 함께 전송
        if (notificationVO.getReferenceType() != null) {
            messageBuilder.putData("referenceType", notificationVO.getReferenceType().toString());
        }

        // referenceId가 존재할 경우 함께 전송
        if (notificationVO.getReferenceType() != null) {
            messageBuilder.putData("referenceId", String.valueOf(notificationVO.getReferenceId()));
        }

        return messageBuilder.build();
    }
}
