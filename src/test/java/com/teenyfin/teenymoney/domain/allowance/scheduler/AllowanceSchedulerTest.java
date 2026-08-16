package com.teenyfin.teenymoney.domain.allowance.scheduler;

import com.teenyfin.teenymoney.domain.allowance.service.AllowanceScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AllowanceSchedulerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    // 스케줄러가 돌면 Clock으로 계산한 "오늘" 날짜로 service.processDuePayments()를 호출하는지
    @Test
    @DisplayName("오늘 날짜로 processDuePayments를 호출한다")
    void triggersProcessingForTodaysDate() {
        AllowanceScheduleService service = mock(AllowanceScheduleService.class);
        AllowanceScheduler scheduler = new AllowanceScheduler(service, CLOCK);

        scheduler.processDuePayments();

        verify(service).processDuePayments(LocalDate.of(2026, 8, 17));
    }

    // 서비스가 예외를 던져도 스케줄러 밖으로 안 새나가는지 (안 새나가야 다음 날 스케줄이 계속 돈다)
    @Test
    @DisplayName("서비스가 예외를 던져도 스케줄러 밖으로 전파하지 않는다 (다음 주기가 계속 돌아야 함)")
    void swallowsFailureSoTheScheduleKeepsRunning() {
        AllowanceScheduleService service = mock(AllowanceScheduleService.class);
        when(service.processDuePayments(any())).thenThrow(new RuntimeException("DB 연결 끊김"));
        AllowanceScheduler scheduler = new AllowanceScheduler(service, CLOCK);

        assertThatCode(scheduler::processDuePayments).doesNotThrowAnyException();
    }

    // @Scheduled 애노테이션에 zone = "Asia/Seoul"이 실제로 붙어있는지 (리플렉션으로 애노테이션 값을 직접 읽음)
    @Test
    @DisplayName("Asia/Seoul 시간대로 매일 실행하도록 설정돼 있다")
    void runsDailyInSeoulTimezone() throws NoSuchMethodException {
        Scheduled scheduled = AllowanceScheduler.class
                .getMethod("processDuePayments")
                .getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("Asia/Seoul", scheduled.zone());
    }
}
