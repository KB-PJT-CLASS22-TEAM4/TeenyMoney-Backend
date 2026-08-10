package com.teenyfin.teenymoney.domain.teenyscore.service;

import com.teenyfin.teenymoney.domain.teenyscore.mapper.TeenyScoreMapper;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
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
                new TeenyScoreGradeService(mapper, clock);

        assertEquals(4, service.applyMonthlyGrades());
        verify(mapper).updateAllActiveChildGrades(appliedAt);
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
                new TeenyScoreGradeService(mapper, clock);

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
                new TeenyScoreGradeService(mapper, clock);

        assertThrows(BusinessException.class,
                () -> service.initializeGrade(999L));
    }
}
