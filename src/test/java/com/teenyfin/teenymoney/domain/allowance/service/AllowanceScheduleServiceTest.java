package com.teenyfin.teenymoney.domain.allowance.service;

import com.teenyfin.teenymoney.domain.allowance.exception.AllowanceErrorCode;
import com.teenyfin.teenymoney.domain.allowance.mapper.AllowanceScheduleMapper;
import com.teenyfin.teenymoney.domain.allowance.vo.AllowanceScheduleVO;
import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AllowanceScheduleServiceTest {

    // 2026-08-10은 월요일이다.
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final MemberPrincipal PARENT = new MemberPrincipal(1L, "PARENT");

    private AllowanceScheduleMapper mapper;
    private FamilyAccessService familyAccessService;
    private AllowanceScheduleProcessor processor;
    private AllowanceScheduleService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AllowanceScheduleMapper.class);
        familyAccessService = mock(FamilyAccessService.class);
        processor = mock(AllowanceScheduleProcessor.class);
        // 실제 생성자 순서: (mapper, familyAccessService, clock, processor)
        service = new AllowanceScheduleService(mapper, familyAccessService, CLOCK, processor);
    }

    // 생성하면 소유권 검증(familyAccessService)을 거치고, nextPaymentDate가 계산돼서 채워지고,
    // mapper.insert가 실제로 호출되는지
    @Test
    @DisplayName("생성 시 오늘(월요일) 기준으로 다음 지급일을 계산해서 저장한다")
    void createSchedulesCalculatesNextPaymentDate() {
        AllowanceScheduleVO created = service.createSchedule(PARENT, 2L, 10_000L, "WEEKLY", 1);

        verify(familyAccessService).requireChildAccess(PARENT, 2L);
        assertEquals(1L, created.getParentId());
        assertEquals(2L, created.getChildId());
        assertEquals(LocalDate.of(2026, 8, 17), created.getNextPaymentDate());
        assertEquals(true, created.isActive());
        verify(mapper).insert(created);
    }

    // DB가 UNIQUE 위반(DuplicateKeyException)을 던지면 우리 쪽 409 에러(SCHEDULE_ALREADY_EXISTS)로
    // 변환해서 다시 던지는지
    @Test
    @DisplayName("이미 그 자녀에게 스케줄이 있으면(UNIQUE 위반) 409로 변환한다")
    void createThrowsConflictOnDuplicateChild() {
        doThrow(new DuplicateKeyException("dup")).when(mapper).insert(any());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createSchedule(PARENT, 2L, 10_000L, "WEEKLY", 1));

        assertEquals(AllowanceErrorCode.SCHEDULE_ALREADY_EXISTS, exception.getErrorCode());
    }

    // WEEKLY인데 payment_day가 범위(1~7) 밖이면 400을 던지고, DB 저장 시도조차 안 하는지
    @Test
    @DisplayName("WEEKLY인데 payment_day가 8이면(범위 밖) 400을 던지고 저장 시도조차 안 한다")
    void createThrowsOnInvalidPaymentDayForWeekly() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createSchedule(PARENT, 2L, 10_000L, "WEEKLY", 8));

        assertEquals(AllowanceErrorCode.INVALID_PAYMENT_DAY, exception.getErrorCode());
        verify(mapper, never()).insert(any());
    }

    // MONTHLY인데 payment_day가 범위(1~28) 밖이면 400을 던지는지
    @Test
    @DisplayName("MONTHLY인데 payment_day가 29면(범위 밖) 400을 던진다")
    void createThrowsOnInvalidPaymentDayForMonthly() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createSchedule(PARENT, 2L, 10_000L, "MONTHLY", 29));

        assertEquals(AllowanceErrorCode.INVALID_PAYMENT_DAY, exception.getErrorCode());
    }

    // 목록 조회가 로그인한 부모의 스케줄만 가져오는지 (쿼리 자체가 parentId로 걸러줌을 확인)
    @Test
    @DisplayName("목록 조회는 로그인한 부모의 스케줄만 가져온다")
    void listReturnsOnlyThatParentsSchedules() {
        AllowanceScheduleVO schedule = new AllowanceScheduleVO();
        schedule.setId(5L);
        schedule.setParentId(1L);
        when(mapper.selectByParentId(1L)).thenReturn(List.of(schedule));

        List<AllowanceScheduleVO> result = service.listSchedules(PARENT);

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).getId());
    }

    // 수정 시 nextPaymentDate가 "오늘 기준"으로 다시 계산되는지
    // (오늘=2026-08-10이고 새 payDay=10이라 "오늘 당일 배제" 규칙에 따라 다음 달 9/10로 계산됨)
    @Test
    @DisplayName("본인 소유 스케줄을 전체 수정하면 next_payment_date도 오늘 기준으로 재계산된다")
    void updateRecalculatesNextPaymentDate() {
        AllowanceScheduleVO existing = existingSchedule();
        when(mapper.selectById(5L)).thenReturn(existing);

        AllowanceScheduleVO updated = service.updateSchedule(PARENT, 5L, 3L, 20_000L, "MONTHLY", 10);

        verify(familyAccessService).requireChildAccess(PARENT, 3L);
        assertEquals(3L, updated.getChildId());
        assertEquals(20_000L, updated.getAmount());
        assertEquals("MONTHLY", updated.getCycleType());
        assertEquals(LocalDate.of(2026, 9, 10), updated.getNextPaymentDate());
        verify(mapper).update(updated);
    }

    // 존재하지 않는 id로 수정을 시도하면 404가 나는지
    @Test
    @DisplayName("존재하지 않는 스케줄을 수정하려 하면 404")
    void updateThrowsNotFoundForMissingSchedule() {
        when(mapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateSchedule(PARENT, 999L, 3L, 20_000L, "MONTHLY", 10));

        assertEquals(AllowanceErrorCode.SCHEDULE_NOT_FOUND, exception.getErrorCode());
    }

    // 존재는 하지만 로그인한 사람 소유가 아닌 스케줄을 수정하려 하면 403이 나는지
    @Test
    @DisplayName("남의 스케줄을 수정하려 하면 403")
    void updateThrowsAccessDeniedForOthersSchedule() {
        AllowanceScheduleVO existing = existingSchedule();
        existing.setParentId(999L);
        when(mapper.selectById(5L)).thenReturn(existing);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateSchedule(PARENT, 5L, 3L, 20_000L, "MONTHLY", 10));

        assertEquals(AllowanceErrorCode.SCHEDULE_ACCESS_DENIED, exception.getErrorCode());
    }

    // 수정으로 childId를 바꿨는데 그 자녀에게 이미 다른 스케줄이 있어서 UNIQUE 위반이 나면 409로 변환하는지
    @Test
    @DisplayName("수정으로 다른 자녀와 UNIQUE 충돌이 나면 409")
    void updateThrowsConflictOnDuplicateChild() {
        AllowanceScheduleVO existing = existingSchedule();
        when(mapper.selectById(5L)).thenReturn(existing);
        doThrow(new DuplicateKeyException("dup")).when(mapper).update(any());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateSchedule(PARENT, 5L, 3L, 20_000L, "MONTHLY", 10));

        assertEquals(AllowanceErrorCode.SCHEDULE_ALREADY_EXISTS, exception.getErrorCode());
    }

    // 꺼져있던 스케줄을 다시 켜면 next_payment_date가 오늘 기준으로 재계산되는지
    @Test
    @DisplayName("비활성 스케줄을 다시 활성화하면 next_payment_date를 오늘 기준으로 재계산한다")
    void reactivatingRecalculatesNextPaymentDate() {
        AllowanceScheduleVO existing = existingSchedule();
        existing.setActive(false);
        existing.setCycleType("WEEKLY");
        existing.setPaymentDay(1);
        existing.setNextPaymentDate(LocalDate.of(2026, 1, 1));
        when(mapper.selectById(5L)).thenReturn(existing);

        AllowanceScheduleVO result = service.updateStatus(PARENT, 5L, true);

        assertEquals(LocalDate.of(2026, 8, 17), result.getNextPaymentDate());
        verify(mapper).updateActiveAndNextPaymentDate(5L, true, LocalDate.of(2026, 8, 17));
    }

    // 이미 켜져 있는 스케줄을 또 켜라고 요청해도(변화 없음) next_payment_date는 그대로인지
    @Test
    @DisplayName("이미 활성인 스케줄을 다시 활성화 요청해도 next_payment_date는 안 바뀐다")
    void reactivatingAlreadyActiveScheduleKeepsNextPaymentDate() {
        AllowanceScheduleVO existing = existingSchedule();
        existing.setActive(true);
        existing.setNextPaymentDate(LocalDate.of(2026, 8, 20));
        when(mapper.selectById(5L)).thenReturn(existing);

        service.updateStatus(PARENT, 5L, true);

        verify(mapper).updateActiveAndNextPaymentDate(5L, true, LocalDate.of(2026, 8, 20));
    }

    // 끄기만 하는 요청은 next_payment_date를 안 건드리고 is_active만 바꾸는지
    @Test
    @DisplayName("비활성화는 next_payment_date를 안 건드리고 is_active만 끈다")
    void deactivatingKeepsNextPaymentDate() {
        AllowanceScheduleVO existing = existingSchedule();
        existing.setActive(true);
        existing.setNextPaymentDate(LocalDate.of(2026, 8, 20));
        when(mapper.selectById(5L)).thenReturn(existing);

        service.updateStatus(PARENT, 5L, false);

        verify(mapper).updateActiveAndNextPaymentDate(5L, false, LocalDate.of(2026, 8, 20));
    }

    // 본인 소유 스케줄을 삭제하면 mapper.deleteById가 실제로 호출되는지
    @Test
    @DisplayName("본인 소유 스케줄을 삭제하면 mapper.deleteById가 호출된다")
    void deleteRemovesOwnedSchedule() {
        AllowanceScheduleVO existing = existingSchedule();
        when(mapper.selectById(5L)).thenReturn(existing);

        service.deleteSchedule(PARENT, 5L);

        verify(mapper).deleteById(5L);
    }

    // 남의 스케줄을 삭제하려 하면 403이 나고, 실제 삭제(deleteById)는 호출되지 않는지
    @Test
    @DisplayName("남의 스케줄을 삭제하려 하면 403이고 deleteById는 호출 안 된다")
    void deleteThrowsAccessDeniedForOthersSchedule() {
        AllowanceScheduleVO existing = existingSchedule();
        existing.setParentId(999L);
        when(mapper.selectById(5L)).thenReturn(existing);

        assertThrows(BusinessException.class, () -> service.deleteSchedule(PARENT, 5L));
        verify(mapper, never()).deleteById(anyLong());
    }

    // 배치 진입점: 오늘 지급일인 id 3개를 조회하면, 그 3개 전부 processor.process()에 하나씩
    // 위임되고 리턴값(처리 대상 건수)도 3인지
    @Test
    @DisplayName("오늘이 지급일인 스케줄 id들을 조회해서 각각 processor에 넘기고 건수를 돌려준다")
    void processDuePaymentsDelegatesToProcessorForEachDueId() {
        LocalDate paymentDate = LocalDate.of(2026, 8, 17);
        when(mapper.selectDueScheduleIds(paymentDate)).thenReturn(List.of(5L, 6L, 7L));

        int count = service.processDuePayments(paymentDate);

        assertEquals(3, count);
        verify(processor).process(5L, paymentDate);
        verify(processor).process(6L, paymentDate);
        verify(processor).process(7L, paymentDate);
    }

    private AllowanceScheduleVO existingSchedule() {
        AllowanceScheduleVO schedule = new AllowanceScheduleVO();
        schedule.setId(5L);
        schedule.setParentId(1L);
        schedule.setChildId(2L);
        schedule.setAmount(10_000L);
        schedule.setCycleType("WEEKLY");
        schedule.setPaymentDay(1);
        schedule.setNextPaymentDate(LocalDate.of(2026, 8, 17));
        schedule.setActive(true);
        return schedule;
    }
}
