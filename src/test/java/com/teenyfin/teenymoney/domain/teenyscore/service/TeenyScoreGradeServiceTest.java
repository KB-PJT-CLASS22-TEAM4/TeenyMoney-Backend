package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.domain.notification.service.NotificationService;
import com.teenyfin.teenymoney.domain.notification.vo.NotificationReferenceType;
import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.domain.teenyscore.vo.TeenyScoreGradeChangeVO;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeenyScoreGradeServiceTest {

    @Test
    void applyMonthlyGradesUsesKoreanCurrentTimeAndReturnsUpdatedCount() {
        TeenyScoreMapper mapper = mock(TeenyScoreMapper.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-31T15:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        LocalDateTime appliedAt = LocalDateTime.of(2026, 9, 1, 0, 0);
        when(mapper.updateAllActiveChildGrades(appliedAt)).thenReturn(4);
        TeenyScoreGradeService service =
                new TeenyScoreGradeService(mapper, clock, mock(NotificationService.class));

        assertEquals(4, service.applyMonthlyGrades());
        verify(mapper).updateAllActiveChildGrades(appliedAt);
    }

    @Test
    void applyMonthlyGradesNotifiesBothChildAndConnectedParent() {
        TeenyScoreMapper mapper = mock(TeenyScoreMapper.class);
        NotificationService notificationService = mock(NotificationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-31T15:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        when(mapper.selectPendingGradeChanges())
                .thenReturn(List.of(gradeChange(2L, 1L, 400, 700)));
        TeenyScoreGradeService service =
                new TeenyScoreGradeService(mapper, clock, notificationService);

        service.applyMonthlyGrades();

        // 등급은 결제 한도와 금리에 연결되므로 자녀와 보호자가 같은 사실을 함께 받는다.
        verify(notificationService).createNotification(
                eq(2L), eq("티니등급이 골드(으)로 올라갔어요"), anyString(),
                eq(NotificationReferenceType.TEENY_SCORE_GRADE), eq(2L), eq(true));
        verify(notificationService).createNotification(
                eq(1L), eq("테스트자녀님의 티니등급이 골드(으)로 올라갔어요"), anyString(),
                eq(NotificationReferenceType.TEENY_SCORE_GRADE), eq(2L), eq(true));
    }

    @Test
    void applyMonthlyGradesSkipsParentNotificationWhenChildHasNoConnection() {
        TeenyScoreMapper mapper = mock(TeenyScoreMapper.class);
        NotificationService notificationService = mock(NotificationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-31T15:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        when(mapper.selectPendingGradeChanges())
                .thenReturn(List.of(gradeChange(2L, null, 700, 400)));
        TeenyScoreGradeService service =
                new TeenyScoreGradeService(mapper, clock, notificationService);

        service.applyMonthlyGrades();

        verify(notificationService).createNotification(
                eq(2L), eq("티니등급이 골드(으)로 내려갔어요"), anyString(),
                eq(NotificationReferenceType.TEENY_SCORE_GRADE), eq(2L), eq(true));
        verifyNoMoreInteractions(notificationService);
    }

    private TeenyScoreGradeChangeVO gradeChange(
            Long childId, Long parentId, int currentMinScore, int newMinScore) {
        TeenyScoreGradeChangeVO change = new TeenyScoreGradeChangeVO();
        change.setChildId(childId);
        change.setChildName("테스트자녀");
        change.setParentId(parentId);
        change.setTeenyScore(newMinScore + 10);
        change.setCurrentGradeName("실버");
        change.setCurrentGradeMinScore(currentMinScore);
        change.setNewGradeName("골드");
        change.setNewGradeMinScore(newMinScore);
        return change;
    }

    @Test
    void initializeGradeUsesStoredScoreToApplyInitialGrade() {
        TeenyScoreMapper mapper = mock(TeenyScoreMapper.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-10T03:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        LocalDateTime appliedAt = LocalDateTime.of(2026, 8, 10, 12, 0);
        when(mapper.initializeAppliedGrade(2L, appliedAt)).thenReturn(1);
        TeenyScoreGradeService service =
                new TeenyScoreGradeService(mapper, clock, mock(NotificationService.class));

        service.initializeGrade(2L);

        verify(mapper).initializeAppliedGrade(2L, appliedAt);
    }

    @Test
    void initializeGradeFailsWhenChildCannotBeInitialized() {
        TeenyScoreMapper mapper = mock(TeenyScoreMapper.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-10T03:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        TeenyScoreGradeService service =
                new TeenyScoreGradeService(mapper, clock, mock(NotificationService.class));

        assertThrows(BusinessException.class,
                () -> service.initializeGrade(999L));
    }
}
