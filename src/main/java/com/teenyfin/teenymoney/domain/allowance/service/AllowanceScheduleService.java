package com.teenyfin.teenymoney.domain.allowance.service;

import com.teenyfin.teenymoney.domain.allowance.exception.AllowanceErrorCode;
import com.teenyfin.teenymoney.domain.allowance.mapper.AllowanceScheduleMapper;
import com.teenyfin.teenymoney.domain.allowance.vo.AllowanceScheduleVO;
import com.teenyfin.teenymoney.domain.family.service.FamilyAccessService;
import com.teenyfin.teenymoney.global.exception.BusinessException;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;


// 정기 용돈 스케줄 CRUD를 담당.
@Service
public class AllowanceScheduleService {

    private final AllowanceScheduleMapper allowanceScheduleMapper;
    private final FamilyAccessService familyAccessService;
    private final Clock clock;


    public AllowanceScheduleService(AllowanceScheduleMapper allowanceScheduleMapper, FamilyAccessService familyAccessService, Clock clock) {
        this.allowanceScheduleMapper = allowanceScheduleMapper;
        this.familyAccessService = familyAccessService;
        this.clock = clock;
    }


    public AllowanceScheduleVO createSchedule(MemberPrincipal principal, Long childId, Long amount, String cycleType, Integer paymentDay) {
        // 부모가 해당 자녀인지 (가족 연동 여부 + 소유권 검증)
        familyAccessService.requireChildAccess(principal, childId);
        validatePaymentDay(cycleType, paymentDay);

        AllowanceScheduleVO schedule = new AllowanceScheduleVO();
        schedule.setParentId(principal.memberId());
        schedule.setChildId(childId);
        schedule.setAmount(amount);
        schedule.setCycleType(cycleType);
        schedule.setPaymentDay(paymentDay);
        // 다음 지급일은 클라이언트가 정하는 게 아니라 서버가 "오늘" 기준으로 계산해서 채움
        schedule.setNextPaymentDate(
                AllowanceScheduleDateCalculator.calculateNext(LocalDate.now(clock), cycleType, paymentDay));
        schedule.setActive(true);

        try {
            allowanceScheduleMapper.insert(schedule);

        } catch (DuplicateKeyException e) {
            throw new BusinessException(AllowanceErrorCode.SCHEDULE_ALREADY_EXISTS);

        }
        return schedule;

    }


    public List<AllowanceScheduleVO> listSchedules(MemberPrincipal principal) {
        return allowanceScheduleMapper.selectByParentId(principal.memberId());
    }

    public AllowanceScheduleVO updateSchedule(MemberPrincipal principal, Long scheduleId, Long childId, Long amount, String cycleType, Integer paymentDay) {

        AllowanceScheduleVO schedule = findOwnedScheduleOrThrow(principal.memberId(), scheduleId);
        familyAccessService.requireChildAccess(principal, childId);
        validatePaymentDay(cycleType, paymentDay);

        schedule.setChildId(childId);
        schedule.setAmount(amount);
        schedule.setCycleType(cycleType);
        schedule.setPaymentDay(paymentDay);
        // 수정할 때도 생성과 똑같이 "오늘" 기준으로 다음 지급일을 다시 계산
        schedule.setNextPaymentDate(
                AllowanceScheduleDateCalculator.calculateNext(LocalDate.now(clock), cycleType, paymentDay));

        try {
            allowanceScheduleMapper.update(schedule);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(AllowanceErrorCode.SCHEDULE_ALREADY_EXISTS);
        }

        return schedule;



    }


    public AllowanceScheduleVO updateStatus(MemberPrincipal principal, Long scheduleId, boolean isActive ) {
        AllowanceScheduleVO schedule = findOwnedScheduleOrThrow(principal.memberId(), scheduleId);

        // 꺼져있던 걸 다시 켤 때만 next_payment_date를 오늘 기준으로 재계산한다.
        // 이미 켜져있던 걸 다시 켜거나(변화 없음), 끄는 경우는 next_payment_date를 그대로 둔다.

        if (isActive && !schedule.isActive()) {
            schedule.setNextPaymentDate(AllowanceScheduleDateCalculator.calculateNext(LocalDate.now(clock), schedule.getCycleType(), schedule.getPaymentDay()));
        }

        schedule.setActive(isActive);

        allowanceScheduleMapper.updateActiveAndNextPaymentDate(schedule.getId(), schedule.isActive(), schedule.getNextPaymentDate());

        return schedule;
    }

    @Transactional
    public void deleteSchedule(MemberPrincipal principal, Long scheduleId) {
        findOwnedScheduleOrThrow(principal.memberId(), scheduleId);
        allowanceScheduleMapper.deleteById(scheduleId);
    }

    // id로 스케줄을 조회하고, 존재+소유권까지 같이 검증하는 헬퍼.

    private AllowanceScheduleVO findOwnedScheduleOrThrow(Long parentId, Long scheduleId) {
        AllowanceScheduleVO schedule = allowanceScheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(AllowanceErrorCode.SCHEDULE_NOT_FOUND);
        }
        if (!schedule.getParentId().equals(parentId)) {
            throw new BusinessException(AllowanceErrorCode.SCHEDULE_ACCESS_DENIED);
        }
        return schedule;
    }

    // DB CHECK 제약(CK_ALW_SCHEDULE_M_PAYMENT_DAY)과 동일한 규칙을 서비스 단에서 먼저 검증해서,
    // 원시 DataIntegrityViolationException 대신 명확한 400으로 막는다.

    // 일주일 단위 는 1~7 만 입력 , 월 단위시 1일 ~28일 까지만 해야함 해당 날짜 유효 검증
    private void validatePaymentDay(String cycleType, Integer paymentDay) {
        boolean valid = ("WEEKLY".equals(cycleType) && paymentDay >= 1 && paymentDay <= 7)
                || ("MONTHLY".equals(cycleType) && paymentDay >= 1 && paymentDay <= 28);
        if (!valid) {
            throw new BusinessException(AllowanceErrorCode.INVALID_PAYMENT_DAY);
        }
    }



}
