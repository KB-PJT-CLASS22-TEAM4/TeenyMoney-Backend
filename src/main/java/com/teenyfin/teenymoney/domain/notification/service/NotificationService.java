package com.teenyfin.teenymoney.domain.notification.service;

import com.teenyfin.teenymoney.domain.notification.dto.NotificationMessage;
import com.teenyfin.teenymoney.domain.notification.dto.request.NotificationFcmRequestDTO;
import com.teenyfin.teenymoney.domain.notification.dto.request.NotificationSettingRequestDTO;
import com.teenyfin.teenymoney.domain.notification.dto.response.NotificationListResponseDTO;
import com.teenyfin.teenymoney.domain.notification.dto.response.NotificationResponseDTO;
import com.teenyfin.teenymoney.domain.notification.dto.response.NotificationSettingResponseDTO;
import com.teenyfin.teenymoney.domain.notification.exception.NotificationErrorCode;
import com.teenyfin.teenymoney.domain.notification.mapper.MemberNotificationMapper;
import com.teenyfin.teenymoney.domain.notification.mapper.NotificationMapper;
import com.teenyfin.teenymoney.domain.notification.vo.MemberNotificationVO;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.exception.CommonErrorCode;
import com.teenyfin.teenymoney.global.sse.SseEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int PAGE_SIZE = 10;
    private static final int FETCH_SIZE = PAGE_SIZE + 1;

    private final NotificationMapper notificationMapper;
    private final MemberNotificationMapper memberNotificationMapper;

    private final FcmService fcmService;

    // 화면 동기화 신호를 흘려보내는 통로. 받는 쪽은 SseEmitterRegistry.onStateChanged()다.
    private final ApplicationEventPublisher eventPublisher;

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

        // ─────────────────────── 화면 동기화 ───────────────────────
        // 알림 설정과 무관하게 항상 발행한다. 이 줄이 아래 return들보다 "위"에 있다는 것이
        // 이 설계의 전부다. 아래로 내리면 알림을 끈 사용자의 화면이 낡은 채로 남는다.
        //
        // 알림 끄기는 "그만 찔러라"이지 "화면을 낡은 상태로 두라"가 아니다. 자녀가 퀘스트
        // 푸시를 껐어도 앱에서 퀘스트 목록을 보고 있다면 방금 생긴 퀘스트는 떠야 하고,
        // 부모가 알림을 껐어도 자녀의 승인 요청은 요청함에 즉시 쌓여야 한다.
        //
        // 여기 한 곳에 두는 이유: 상태가 바뀌는 지점 20곳이 전부 이 메서드를 지난다.
        // 호출부마다 발행하면 21번째가 생길 때 조용히 빠진다.
        // 알림 이력을 남기지 않는 상태 변경은 이걸로 못 잡는다. 그런 곳은 직접 발행한다
        // (TransferService의 부모 지갑, PaymentService의 부모가 보는 자녀 지갑 두 군데).
        eventPublisher.publishEvent(new SseEvent(memberId, notificationReferenceType));

        // ─────────────────────── 여기부터 알림 ───────────────────────
        // 푸시 알림이 필요하지 않은 경우 내역만 남기고 종료
        if (isPushed == null || !isPushed) {
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

        NotificationVO notificationVO = notificationMapper.selectById(notificationId);

        if (notificationVO == null) {
            throw new BusinessException(NotificationErrorCode.INVALID_NOTIFICATION_ID);
        }

        // 자신의 알림이 맞는지 확인
        if (!Objects.equals(notificationVO.getMemberId(), memberId)) {
            throw new BusinessException(NotificationErrorCode.FORBIDDEN_TO_NOTIFICATION);
        }

        notificationMapper.updateIsReadTrue(notificationId);
    }

    // 전체 알림 읽음 처리
    @Transactional
    public void readAllNotifications(Long memberId, Long notificationId) {

        NotificationVO notificationVO = notificationMapper.selectById(notificationId);

        if (notificationVO == null) {
            throw new BusinessException(NotificationErrorCode.INVALID_NOTIFICATION_ID);
        }

        // 자신의 알림이 맞는지 확인
        if (!Objects.equals(notificationVO.getMemberId(), memberId)) {
            throw new BusinessException(NotificationErrorCode.FORBIDDEN_TO_NOTIFICATION);
        }

        notificationMapper.updateAllIsReadTrueCreatedBeforeLatestTime(memberId, notificationVO.getCreatedAt());
    }

    // 아직 읽지 않은 알림 개수 조회
    @Transactional(readOnly = true)
    public Integer getUnreadNotificationCount(Long memberId) {

        return notificationMapper.countIsReadFalse(memberId);
    }

    // FCM 토큰 수정
    @Transactional
    public void modifyFcmToken(Long memberId, NotificationFcmRequestDTO notificationFcmRequestDTO) {

        memberNotificationMapper.updateFcmToken(memberId, notificationFcmRequestDTO.getFcmToken());
    }

    // 알림 수신 여부 조회
    @Transactional(readOnly = true)
    public NotificationSettingResponseDTO getNotificationSetting(Long memberId) {

        MemberNotificationVO memberNotificationVO = memberNotificationMapper.selectNotificationInfo(memberId);

        return NotificationSettingResponseDTO.builder()
                .notificationQuest(memberNotificationVO.getNotiQuest())
                .notificationFinance(memberNotificationVO.getNotiFinance())
                .notificationPayment(memberNotificationVO.getNotiPayment())
                .build();
    }

    // 알림 수신 여부 변경
    @Transactional
    public void modifyNotificationSetting(Long memberId, NotificationSettingRequestDTO notificationSettingRequestDTO) {

        memberNotificationMapper.updateNotificationSetting(memberId, notificationSettingRequestDTO);
    }
}
