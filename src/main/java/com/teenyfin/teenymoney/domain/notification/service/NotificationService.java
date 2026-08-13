package com.teenyfin.teenymoney.domain.notification.service;

import com.teenyfin.teenymoney.domain.notification.dto.NotificationMessage;
import com.teenyfin.teenymoney.domain.notification.dto.response.NotificationResponseDTO;
import com.teenyfin.teenymoney.domain.notification.mapper.MemberNotificationMapper;
import com.teenyfin.teenymoney.domain.notification.mapper.NotificationMapper;
import com.teenyfin.teenymoney.domain.notification.vo.MemberNotificationVO;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    // 최근 30일 간의 알림 내역을 최신순으로 조회한다.
    @Transactional
    public List<NotificationResponseDTO> getNotifications(Long memberId) {

        List<NotificationVO> notificationVOList = notificationMapper.selectRecentNotifications(memberId);

        return notificationVOList.stream()
                .map(x -> NotificationResponseDTO.builder()
                        .id(x.getId())
                        .memberId(x.getMemberId())
                        .title(x.getTitle())
                        .content(x.getContent())
                        .referenceType(x.getReferenceType())
                        .referenceId(x.getReferenceId())
                        .isRead(x.getIsRead())
                        .createdAt(x.getCreatedAt())
                        .build())
                .toList();
    }

    // 단일 알림 읽음 처리
    @Transactional
    public void readNotification(Long memberId, Long notificationId) {

        notificationMapper.updateIsReadTrue(notificationId);
    }

    // 전체 알림 읽음 처리
    @Transactional
    public void readAllNotifications(Long memberId) {

        notificationMapper.updateAllIsReadTrue(memberId);
    }

    // 아직 읽지 않은 알림 개수 조회
    @Transactional(readOnly = true)
    public Integer getUnreadNotificationCount(Long memberId) {

        return notificationMapper.countIsReadFalse(memberId);
    }
}
