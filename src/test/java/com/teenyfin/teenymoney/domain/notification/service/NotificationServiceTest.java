package com.teenyfin.teenymoney.domain.notification.service;

import com.google.firebase.messaging.Message;
import com.teenyfin.teenymoney.domain.notification.mapper.MemberNotificationMapper;
import com.teenyfin.teenymoney.domain.notification.mapper.NotificationMapper;
import com.teenyfin.teenymoney.domain.notification.vo.MemberNotificationVO;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// 순수 Mockito 단위 테스트. NotificationMapper/MemberNotificationMapper/FcmService를 전부
// mock으로 대체해서 Spring 컨텍스트도, 진짜 DB도 필요 없다.
//
// RootConfig를 로드하는 통합 테스트로 만들면 RootConfig가 도메인 전체를 컴포넌트 스캔하다가
// (Toss 연동용) TossPaymentsClient가 RestTemplate 빈을 요구하는데, LazyBeanInitializer 없이
// RootConfig만 로드하는 다른 도메인의 기존 테스트(MemberMapperTest, TransferServiceTest 등)와
// 같은 SpringExtension 컨텍스트 캐시를 공유하면서 컨텍스트 로딩 자체가 깨지는 문제에 얽힌다.
// createNotification()의 분기 로직(DB 저장/푸시 여부/채널별 옵트아웃)은 순수 로직이라
// mock만으로 충분히 검증되므로, 그 불안정한 공유 컨텍스트에 기댈 이유가 없다.
// 매퍼 SQL 자체가 실제 컬럼과 맞는지는 NotificationMapperTest/MemberNotificationMapperTest가
// 진짜 DB로 따로 검증한다.
class NotificationServiceTest {

    private NotificationMapper notificationMapper;
    private MemberNotificationMapper memberNotificationMapper;
    private FcmService fcmService;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationMapper = mock(NotificationMapper.class);
        memberNotificationMapper = mock(MemberNotificationMapper.class);
        fcmService = mock(FcmService.class);
        notificationService = new NotificationService(notificationMapper, memberNotificationMapper, fcmService);
    }

    private MemberNotificationVO memberInfo(String fcmToken, boolean notiPayment, boolean notiQuest, boolean notiFinance, boolean notiAllowance) {
        return MemberNotificationVO.builder()
                .fcmToken(fcmToken)
                .notiPayment(notiPayment)
                .notiQuest(notiQuest)
                .notiFinance(notiFinance)
                .notiAllowance(notiAllowance)
                .build();
    }

    @Test
    void createNotificationAlwaysInsertsNotificationRow() {
        notificationService.createNotification(
                1L, "결제가 완료됐어요", "GS25 강남점 · 3,200원",
                NotificationReferenceType.PAYMENT, 10L, false);

        ArgumentCaptor<NotificationVO> captor = ArgumentCaptor.forClass(NotificationVO.class);
        verify(notificationMapper, times(1)).insert(captor.capture());

        NotificationVO stored = captor.getValue();
        assertEquals(1L, stored.getMemberId());
        assertEquals("결제가 완료됐어요", stored.getTitle());
        assertEquals("GS25 강남점 · 3,200원", stored.getContent());
        assertEquals(NotificationReferenceType.PAYMENT, stored.getReferenceType());
        assertEquals(10L, stored.getReferenceId());
    }

    @Test
    void createNotificationDoesNotLookUpMemberOrPushWhenIsPushedFalse() {
        notificationService.createNotification(
                1L, "제목", "내용", NotificationReferenceType.PAYMENT, 10L, false);

        // then: 푸시 여부를 판단할 필요가 없으니 회원 알림 설정 조회 자체를 안 해야 한다
        verifyNoInteractions(memberNotificationMapper);
        verifyNoInteractions(fcmService);
    }

    @Test
    void createNotificationPushesWhenIsPushedTrueAndChannelEnabledAndTokenPresent() {
        when(memberNotificationMapper.selectNotificationInfo(1L))
                .thenReturn(memberInfo("dummy-fcm-token", true, true, true, true));
        Message dummyMessage = Message.builder().setToken("dummy-fcm-token").build();
        when(fcmService.createMessage(eq("dummy-fcm-token"), any(), any(NotificationVO.class)))
                .thenReturn(dummyMessage);

        notificationService.createNotification(
                1L, "결제가 완료됐어요", "GS25 강남점 · 3,200원",
                NotificationReferenceType.PAYMENT, 10L, true);

        verify(fcmService, times(1)).send(dummyMessage);
    }

    @Test
    void createNotificationSkipsPushWhenFcmTokenMissing() {
        when(memberNotificationMapper.selectNotificationInfo(1L))
                .thenReturn(memberInfo(null, true, true, true, true));

        notificationService.createNotification(
                1L, "제목", "내용", NotificationReferenceType.PAYMENT, 10L, true);

        verify(notificationMapper, times(1)).insert(any());
        verifyNoInteractions(fcmService);
    }

    @Test
    void createNotificationSkipsPushWhenFcmTokenBlank() {
        when(memberNotificationMapper.selectNotificationInfo(1L))
                .thenReturn(memberInfo("   ", true, true, true, true));

        notificationService.createNotification(
                1L, "제목", "내용", NotificationReferenceType.PAYMENT, 10L, true);

        verifyNoInteractions(fcmService);
    }

    @Test
    void createNotificationSkipsPushWhenPaymentChannelDisabled() {
        when(memberNotificationMapper.selectNotificationInfo(1L))
                .thenReturn(memberInfo("dummy-fcm-token", false, true, true, true));

        notificationService.createNotification(
                1L, "제목", "내용", NotificationReferenceType.PAYMENT, 10L, true);

        // then: 내역 저장은 되지만, 결제 알림을 꺼둔 회원이라 푸시는 안 나가야 한다
        verify(notificationMapper, times(1)).insert(any());
        verifyNoInteractions(fcmService);
    }

    @Test
    void createNotificationSkipsPushWhenQuestChannelDisabled() {
        when(memberNotificationMapper.selectNotificationInfo(1L))
                .thenReturn(memberInfo("dummy-fcm-token", true, false, true, true));

        notificationService.createNotification(
                1L, "제목", "내용", NotificationReferenceType.QUEST, 10L, true);

        verifyNoInteractions(fcmService);
    }

    @Test
    void createNotificationSkipsPushWhenFinanceChannelDisabledForDepositEnrollment() {
        when(memberNotificationMapper.selectNotificationInfo(1L))
                .thenReturn(memberInfo("dummy-fcm-token", true, true, false, true));

        notificationService.createNotification(
                1L, "제목", "내용", NotificationReferenceType.DEPOSIT_ENROLLMENT, 10L, true);

        verifyNoInteractions(fcmService);
    }

    @Test
    void createNotificationSkipsPushWhenFinanceChannelDisabledForSavingEnrollment() {
        when(memberNotificationMapper.selectNotificationInfo(1L))
                .thenReturn(memberInfo("dummy-fcm-token", true, true, false, true));

        notificationService.createNotification(
                1L, "제목", "내용", NotificationReferenceType.SAVING_ENROLLMENT, 10L, true);

        verifyNoInteractions(fcmService);
    }

    @Test
    void createNotificationSkipsPushWhenFinanceChannelDisabledForLoanEnrollment() {
        when(memberNotificationMapper.selectNotificationInfo(1L))
                .thenReturn(memberInfo("dummy-fcm-token", true, true, false, true));

        notificationService.createNotification(
                1L, "제목", "내용", NotificationReferenceType.LOAN_ENROLLMENT, 10L, true);

        verifyNoInteractions(fcmService);
    }

    @Test
    void createNotificationBuildsMessageFromStoredNotificationBeforeSending() {
        when(memberNotificationMapper.selectNotificationInfo(1L))
                .thenReturn(memberInfo("dummy-fcm-token", true, true, true, true));

        notificationService.createNotification(
                1L, "결제가 완료됐어요", "GS25 강남점 · 3,200원",
                NotificationReferenceType.PAYMENT, 10L, true);

        // then: 방금 저장한 NotificationVO(title/content 포함)를 그대로 메시지 생성에 넘긴다
        ArgumentCaptor<NotificationVO> captor = ArgumentCaptor.forClass(NotificationVO.class);
        verify(fcmService, times(1))
                .createMessage(eq("dummy-fcm-token"), any(), captor.capture());
        assertEquals("결제가 완료됐어요", captor.getValue().getTitle());
        assertEquals("GS25 강남점 · 3,200원", captor.getValue().getContent());
    }
}