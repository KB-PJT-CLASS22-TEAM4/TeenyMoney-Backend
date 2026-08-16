package com.teenyfin.teenymoney.domain.notification.service;

import com.teenyfin.teenymoney.domain.notification.dto.NotificationMessage;
import com.teenyfin.teenymoney.domain.notification.dto.response.NotificationListResponseDTO;
import com.teenyfin.teenymoney.domain.notification.dto.response.NotificationResponseDTO;
import com.teenyfin.teenymoney.domain.notification.mapper.MemberNotificationMapper;
import com.teenyfin.teenymoney.domain.notification.mapper.NotificationMapper;
import com.teenyfin.teenymoney.domain.notification.vo.MemberNotificationVO;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int PAGE_SIZE = 10;
    private static final int FETCH_SIZE = PAGE_SIZE + 1;

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

    // 최근 30일 간의 알림 내역을 최신순으로 10건씩 커서 기반 조회한다.
    @Transactional(readOnly = true)
    public NotificationListResponseDTO getNotifications(Long memberId, String encodedCursor) {

        Cursor cursor = decodeCursor(encodedCursor);

        List<NotificationVO> rows = notificationMapper.selectRecentNotifications(
                memberId,
                cursor == null ? null : cursor.createdAt(),
                cursor == null ? null : cursor.id(),
                FETCH_SIZE);

        boolean hasNext = rows.size() > PAGE_SIZE;
        List<NotificationVO> page = hasNext ? rows.subList(0, PAGE_SIZE) : rows;

        List<NotificationResponseDTO> notifications = page.stream()
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

        String nextCursor = hasNext ? encodeCursor(page.get(PAGE_SIZE - 1)) : null;

        return NotificationListResponseDTO.builder()
                .notifications(notifications)
                .nextCursor(nextCursor)
                .build();
    }

    private String encodeCursor(NotificationVO notification) {
        String value = notification.getCreatedAt() + "|" + notification.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String encodedCursor) {
        if (encodedCursor == null || encodedCursor.isBlank()) {
            return null;
        }
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(encodedCursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return new Cursor(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException exception) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }

    private record Cursor(LocalDateTime createdAt, Long id) {
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
