package com.teenyfin.teenymoney.domain.allowance.mapper;

import com.teenyfin.teenymoney.domain.allowance.vo.AllowanceScheduleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AllowanceScheduleMapper {


    void insert(AllowanceScheduleVO schedule);

    // 특정 부모의 스케줄 전체 목록 (자녀별로 여러 건 가능해서 List)
    List<AllowanceScheduleVO> selectByParentId(@Param("parentId") Long parentId);

    // id 하나로 스케줄 단건 조회. 소유권 확인 및 배치 처리 시 씀.
    AllowanceScheduleVO selectById(@Param("id") Long id);

    // 전체 수정 (childId 변경 포함). UNIQUE(parent_id, child_id) 위반 시
    // DuplicateKeyException이 나며, 호출부(Service)에서 잡아 409로 변환한다.
    void update(AllowanceScheduleVO schedule);

    // /status 토글 전용 - 활성 여부와 (재활성화 시 재계산된) next_payment_date를 함께 갱신.
    void updateActiveAndNextPaymentDate(
            @Param("id") Long id,
            @Param("isActive") boolean isActive,
            @Param("nextPaymentDate") LocalDate nextPaymentDate);

    // 배치가 지급 처리(성공/실패 불문)를 마친 뒤 다음 회차로 넘길 때 씀.
    void updateNextPaymentDate(
            @Param("id") Long id,
            @Param("nextPaymentDate") LocalDate nextPaymentDate);

    void deleteById(@Param("id") Long id);

    // 오늘이 지급일인 활성 스케줄의 id만 뽑는다 - 배치 조회 기준.
    List<Long> selectDueScheduleIds(@Param("paymentDate") LocalDate paymentDate);
}
