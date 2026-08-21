package com.teenyfin.teenymoney.domain.notification.service;

import com.google.firebase.messaging.Message;
import com.teenyfin.teenymoney.domain.notification.dto.response.NotificationListResponseDTO;
import com.teenyfin.teenymoney.domain.notification.mapper.MemberNotificationMapper;
import com.teenyfin.teenymoney.domain.notification.mapper.NotificationMapper;
import com.teenyfin.teenymoney.domain.notification.vo.MemberNotificationVO;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.sse.SseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private ApplicationEventPublisher eventPublisher;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationMapper = mock(NotificationMapper.class);
        memberNotificationMapper = mock(MemberNotificationMapper.class);
        fcmService = mock(FcmService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        notificationService = new NotificationService(
                notificationMapper, memberNotificationMapper, fcmService, eventPublisher);
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

    // ─────────── 화면 동기화 회귀 테스트 ───────────
    // SSE 발행 한 줄이 알림 설정 검사 "아래"로 내려가는 순간 아래 두 테스트가 깨진다.
    // 그게 이 기능의 존재 이유를 지키는 유일한 자동 검증이다.

    @Test
    void 알림_설정이_꺼져_있어도_화면_동기화_신호는_발행된다() {
        // given: 퀘스트 푸시를 끈 회원
        when(memberNotificationMapper.selectNotificationInfo(1L))
                .thenReturn(memberInfo("dummy-fcm-token", true, false, true, true));

        notificationService.createNotification(
                1L, "새 퀘스트가 도착했어요", "방 청소",
                NotificationReferenceType.QUEST, 10L, true);

        // then: FCM은 나가지 않는다 (사용자가 껐으니 당연하다)
        verify(fcmService, never()).send(any());

        // then: 그래도 화면은 갱신돼야 한다. 알림 끄기는 "그만 찔러라"이지
        //       "화면을 낡은 상태로 두라"가 아니다.
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());

        assertThat(captor.getValue())
                .isEqualTo(new SseEvent(1L, NotificationReferenceType.QUEST));
    }

    @Test
    void 푸시를_보내지_않는_알림도_화면_동기화_신호는_발행된다() {
        // isPushed=false는 "이력만 남긴다"는 뜻이지 "상태가 안 바뀌었다"가 아니다.
        notificationService.createNotification(
                1L, "결제가 완료됐어요", "GS25 강남점 · 3,200원",
                NotificationReferenceType.PAYMENT, 10L, false);

        verify(eventPublisher, times(1))
                .publishEvent(new SseEvent(1L, NotificationReferenceType.PAYMENT));
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
    void createNotificationPushesCategoryPolicyChangeWithoutReferenceId() {
        // CATEGORY_POLICY는 결제/퀘스트/금융상품 어떤 채널에도 속하지 않으므로
        // isFinanceType() 등 switch 분기를 반드시 거치면서도 예외 없이 통과해야 한다.
        when(memberNotificationMapper.selectNotificationInfo(1L))
                .thenReturn(memberInfo("dummy-fcm-token", true, true, true, true));
        Message dummyMessage = Message.builder().setToken("dummy-fcm-token").build();
        when(fcmService.createMessage(eq("dummy-fcm-token"), any(), any(NotificationVO.class)))
                .thenReturn(dummyMessage);

        notificationService.createNotification(
                1L, "카테고리 제한 설정이 바뀌었어요", "편의점",
                NotificationReferenceType.CATEGORY_POLICY, null, true);

        verify(fcmService, times(1)).send(dummyMessage);
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

    // ---------- getNotifications() ----------

    private NotificationVO notificationRow(long id, LocalDateTime createdAt) {
        return NotificationVO.builder()
                .id(id)
                .memberId(1L)
                .title("제목" + id)
                .content("내용" + id)
                .referenceType(NotificationReferenceType.PAYMENT)
                .referenceId(id)
                .isRead(false)
                .createdAt(createdAt)
                .build();
    }

    @Test
    void getNotificationsFirstPageWithNoCursor() {
        LocalDateTime now = LocalDateTime.now();
        // 마퍼가 11건(FETCH_SIZE)을 최신순으로 반환한다고 가정 -> 10건만 남고 다음 페이지가 있다고 판단해야 한다
        List<NotificationVO> rows = IntStream.rangeClosed(1, 11)
                .mapToObj(i -> notificationRow(12 - i, now.minusMinutes(i)))
                .toList();

        when(notificationMapper.selectRecentNotifications(eq(1L), isNull(), isNull(), eq(11)))
                .thenReturn(rows);

        NotificationListResponseDTO result = notificationService.getNotifications(1L, null);

        assertThat(result.getNotifications()).hasSize(10);
        assertThat(result.getNextCursor()).isNotNull();
        // then: 응답엔 마지막(가장 오래된) 항목까지 정확히 10건만 담긴다
        assertEquals(11L, result.getNotifications().get(0).getId());
        assertEquals(2L, result.getNotifications().get(9).getId());
    }

    @Test
    void getNotificationsLastPageHasNoNextCursor() {
        LocalDateTime now = LocalDateTime.now();
        // 마퍼가 PAGE_SIZE(10)건 이하로 반환하면 더 가져올 페이지가 없다
        List<NotificationVO> rows = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> notificationRow(i, now.minusMinutes(i)))
                .toList();

        when(notificationMapper.selectRecentNotifications(eq(1L), isNull(), isNull(), eq(11)))
                .thenReturn(rows);

        NotificationListResponseDTO result = notificationService.getNotifications(1L, null);

        assertThat(result.getNotifications()).hasSize(5);
        assertThat(result.getNextCursor()).isNull();
    }

    @Test
    void getNotificationsSecondPageDecodesCursorFromFirstPage() {
        LocalDateTime now = LocalDateTime.now();
        List<NotificationVO> firstPageRows = IntStream.rangeClosed(1, 11)
                .mapToObj(i -> notificationRow(12 - i, now.minusMinutes(i)))
                .toList();
        when(notificationMapper.selectRecentNotifications(eq(1L), isNull(), isNull(), eq(11)))
                .thenReturn(firstPageRows);

        String cursor = notificationService.getNotifications(1L, null).getNextCursor();

        // 커서가 가리키는 값(10번째로 반환된, id=2인 행)의 createdAt/id로 다음 페이지를 요청해야 한다
        NotificationVO lastOfFirstPage = firstPageRows.get(9);
        when(notificationMapper.selectRecentNotifications(
                eq(1L), eq(lastOfFirstPage.getCreatedAt()), eq(lastOfFirstPage.getId()), eq(11)))
                .thenReturn(List.of(notificationRow(1L, now.minusMinutes(11))));

        NotificationListResponseDTO secondPage = notificationService.getNotifications(1L, cursor);

        assertThat(secondPage.getNotifications()).hasSize(1);
        assertThat(secondPage.getNotifications().get(0).getId()).isEqualTo(1L);
        assertThat(secondPage.getNextCursor()).isNull();
    }

    @Test
    void getNotificationsThrowsOnInvalidCursor() {
        assertThatThrownBy(() -> notificationService.getNotifications(1L, "not-a-valid-cursor!!"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(notificationMapper);
    }
}