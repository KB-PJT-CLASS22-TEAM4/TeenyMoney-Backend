package com.teenyfin.teenymoney.domain.notification.service;

import com.teenyfin.teenymoney.domain.notification.dto.NotificationMessage;
import com.teenyfin.teenymoney.domain.notification.mapper.MemberNotificationMapper;
import com.teenyfin.teenymoney.domain.notification.mapper.NotificationMapper;
import com.teenyfin.teenymoney.domain.notification.vo.MemberNotificationVO;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final MemberNotificationMapper memberNotificationMapper;

    private final FcmService fcmService;

    // 알림 내역을 DB에 저장하고 푸시 알림 요청
    @Transactional
    public void createNotification(Long memberId, String title, String content, NotificationReferenceType notificationReferenceType, Long referenceId, Boolean isPushed) {

        NotificationVO notificationVO = NotificationVO.builder()
                .memberId(memberId)
                .title(title)
                .content(content)
                .referenceType(notificationReferenceType)
                .referenceId(referenceId)
                .build();

        notificationMapper.insert(notificationVO);

        // 푸시 알림이 필요하지 않은 경우 내역만 남기고 종료
        if (!isPushed) {
            return;
        }

        MemberNotificationVO memberNotificationVO = memberNotificationMapper.selectNotificationInfo(memberId);

        // 결제 관련 알림을 끈 경우
        if (notificationReferenceType == NotificationReferenceType.PAYMENT && !memberNotificationVO.getNotiPayment()) {
            return;
        }

        // 퀘스트 관련 알림을 끈 경우
        if (notificationReferenceType == NotificationReferenceType.QUEST && !memberNotificationVO.getNotiQuest()) {
            return;
        }

        // 금융 상품 관련 알림을 끈 경우
        if ((notificationReferenceType == NotificationReferenceType.SAVING_ENROLLMENT
                || notificationReferenceType == NotificationReferenceType.DEPOSIT_ENROLLMENT
                || notificationReferenceType == NotificationReferenceType.LOAN_ENROLLMENT)
                && !memberNotificationVO.getNotiFinance()) {
            return;
        }

        sendNotification(memberNotificationVO.getFcmToken(), notificationVO);
    }

    public void sendNotification(String fcmToken, NotificationVO notificationVO) {
        if (fcmToken != null && !fcmToken.isBlank()) {
            NotificationMessage notificationMessage = NotificationMessage.of("티니머니", notificationVO.getTitle());
            fcmService.send(fcmService.createMessage(fcmToken, notificationMessage, notificationVO));
        }
    }
}
