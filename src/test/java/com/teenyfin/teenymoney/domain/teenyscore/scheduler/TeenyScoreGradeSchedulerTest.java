package com.teenyfin.teenymoney.domain.teenyscore.scheduler;

import com.teenyfin.teenymoney.domain.teenyscore.service.TeenyScoreGradeService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TeenyScoreGradeSchedulerTest {

    @Test
    void scheduledUpdateDelegatesToGradeService() {
        TeenyScoreGradeService service = mock(TeenyScoreGradeService.class);
        TeenyScoreGradeScheduler scheduler =
                new TeenyScoreGradeScheduler(service);

        scheduler.updateMonthlyGrades();

        verify(service).applyMonthlyGrades();
    }
}
