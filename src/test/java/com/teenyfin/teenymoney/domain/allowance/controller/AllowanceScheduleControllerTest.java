package com.teenyfin.teenymoney.domain.allowance.controller;

import com.teenyfin.teenymoney.domain.allowance.dto.request.AllowanceScheduleCreateRequestDTO;
import com.teenyfin.teenymoney.domain.allowance.dto.request.AllowanceScheduleStatusRequestDTO;
import com.teenyfin.teenymoney.domain.allowance.dto.request.AllowanceScheduleUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.allowance.dto.response.AllowanceScheduleResponseDTO;
import com.teenyfin.teenymoney.domain.allowance.service.AllowanceScheduleService;
import com.teenyfin.teenymoney.domain.allowance.vo.AllowanceScheduleVO;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AllowanceScheduleControllerTest {

    private static final MemberPrincipal PARENT = new MemberPrincipal(1L, "PARENT");

    private AllowanceScheduleService service;
    private AllowanceScheduleController controller;

    @BeforeEach
    void setUp() {
        service = mock(AllowanceScheduleService.class);
        controller = new AllowanceScheduleController(service);
    }

    // 생성 요청 DTO의 필드들을 풀어서 service.createSchedule(...)에 정확히 그대로 넘기고,
    // 응답을 ApiResponse로 잘 감싸는지
    @Test
    @DisplayName("생성 요청을 DTO에서 풀어서 서비스로 그대로 전달한다")
    void createSchedulePassesThroughToService() {
        AllowanceScheduleCreateRequestDTO request = new AllowanceScheduleCreateRequestDTO();
        request.setChildId(2L);
        request.setAmount(10_000L);
        request.setCycleType("WEEKLY");
        request.setPaymentDay(1);
        when(service.createSchedule(PARENT, 2L, 10_000L, "WEEKLY", 1))
                .thenReturn(schedule());

        ApiResponse<AllowanceScheduleResponseDTO> response =
                controller.createSchedule(PARENT, request);

        assertTrue(response.isSuccess());
        assertEquals(5L, response.getData().getId());
    }

    // List<VO> -> List<DTO> 변환이 잘 되는지 (서비스가 준 결과 개수만큼 응답에도 그대로 담기는지)
    @Test
    @DisplayName("목록 조회는 서비스 결과를 DTO 리스트로 감싸서 돌려준다")
    void listSchedulesWrapsServiceResult() {
        when(service.listSchedules(PARENT)).thenReturn(List.of(schedule()));

        ApiResponse<List<AllowanceScheduleResponseDTO>> response = controller.listSchedules(PARENT);

        assertEquals(1, response.getData().size());
    }

    // PATCH 전체수정 요청도 DTO -> Service 파라미터 매핑이 정확히 맞는지
    @Test
    @DisplayName("전체 수정 요청을 DTO에서 풀어서 서비스로 그대로 전달한다")
    void updateSchedulePassesThroughToService() {
        AllowanceScheduleUpdateRequestDTO request = new AllowanceScheduleUpdateRequestDTO();
        request.setChildId(3L);
        request.setAmount(20_000L);
        request.setCycleType("MONTHLY");
        request.setPaymentDay(10);
        when(service.updateSchedule(PARENT, 5L, 3L, 20_000L, "MONTHLY", 10))
                .thenReturn(schedule());

        ApiResponse<AllowanceScheduleResponseDTO> response =
                controller.updateSchedule(PARENT, 5L, request);

        assertTrue(response.isSuccess());
        verify(service).updateSchedule(PARENT, 5L, 3L, 20_000L, "MONTHLY", 10);
    }

    // 상태 토글 요청에서 isActive 값만 정확히 뽑아 서비스로 넘기는지
    @Test
    @DisplayName("상태 토글 요청은 isActive 값만 뽑아서 서비스로 전달한다")
    void updateStatusPassesIsActiveToService() {
        AllowanceScheduleStatusRequestDTO request = new AllowanceScheduleStatusRequestDTO();
        request.setIsActive(false);
        when(service.updateStatus(PARENT, 5L, false)).thenReturn(schedule());

        controller.updateStatus(PARENT, 5L, request);

        verify(service).updateStatus(PARENT, 5L, false);
    }

    // 삭제 요청이 서비스에 위임되고, 데이터 없는 빈 성공 응답이 오는지
    @Test
    @DisplayName("삭제 요청은 서비스에 위임하고 빈 성공 응답을 돌려준다")
    void deleteSchedulePassesThroughToService() {
        ApiResponse<Void> response = controller.deleteSchedule(PARENT, 5L);

        verify(service).deleteSchedule(PARENT, 5L);
        assertTrue(response.isSuccess());
    }

    private AllowanceScheduleVO schedule() {
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
