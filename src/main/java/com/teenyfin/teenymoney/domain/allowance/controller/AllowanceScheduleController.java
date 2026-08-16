package com.teenyfin.teenymoney.domain.allowance.controller;


import com.teenyfin.teenymoney.domain.allowance.dto.request.AllowanceScheduleCreateRequestDTO;
import com.teenyfin.teenymoney.domain.allowance.dto.request.AllowanceScheduleStatusRequestDTO;
import com.teenyfin.teenymoney.domain.allowance.dto.request.AllowanceScheduleUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.allowance.dto.response.AllowanceScheduleResponseDTO;
import com.teenyfin.teenymoney.domain.allowance.service.AllowanceScheduleService;
import com.teenyfin.teenymoney.domain.allowance.vo.AllowanceScheduleVO;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/allowance/schedule")
@Api(tags = "AllowanceSchedule", description = "정기 용돈 스케줄 API")  // Swagger UI에서 이 컨트롤러가
// 묶이는 그룹 이름/설명
public class AllowanceScheduleController {

    private final AllowanceScheduleService allowanceScheduleService;


    public AllowanceScheduleController(AllowanceScheduleService allowanceScheduleService) {
        this.allowanceScheduleService = allowanceScheduleService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "정기 용돈 스케줄 생성",
            notes = "지정한 자녀에게 정해진 주기(매주/매달)로 자동 용돈을 지급하도록 설정합니다. 생성 시 자동으로 활성화됩니다.",
            authorizations = { @io.swagger.annotations.Authorization(value = "JWT") }
    )
    @ApiResponses({   // Swagger UI에 나열될 "가능한 응답 코드별 의미" 목록 (실제 로직에 영향 없음,
            // 순수 문서 목적)
            @io.swagger.annotations.ApiResponse(code = 200, message = "생성 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "요청 값이 올바르지 않음"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "연동되지 않은 자녀"),
            @io.swagger.annotations.ApiResponse(code = 409, message = "이미 해당 자녀에게 등록된 스케줄이 있음") })

    public ApiResponse<AllowanceScheduleResponseDTO> createSchedule(@AuthenticationPrincipal MemberPrincipal principal,
                                                                    @Valid @RequestBody AllowanceScheduleCreateRequestDTO request) {
        AllowanceScheduleVO schedule = allowanceScheduleService.createSchedule(principal, request.getChildId(), request.getAmount(),
                request.getCycleType(), request.getPaymentDay());

        return ApiResponse.ok(AllowanceScheduleResponseDTO.of(schedule));
    }

    @GetMapping   // GET /allowance/schedule
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "정기 용돈 스케줄 목록 조회",
            notes = "로그인한 부모가 등록한 정기 용돈 스케줄 전체를 조회합니다.",
            authorizations = { @io.swagger.annotations.Authorization(value = "JWT") }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "조회 성공"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요") })
    public ApiResponse<List<AllowanceScheduleResponseDTO>> listSchedules(
            @AuthenticationPrincipal MemberPrincipal principal) {

        List<AllowanceScheduleResponseDTO> response = allowanceScheduleService.listSchedules(principal)
                .stream()
                .map(AllowanceScheduleResponseDTO::of)
                .toList();
        return ApiResponse.ok(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "정기 용돈 스케줄 전체 수정",
            notes = "대상 자녀/금액/주기/지급일을 전체 교체합니다. 부분 수정이 아니므로 모든 필드를 다시 보내야 합니다.",
            authorizations = { @io.swagger.annotations.Authorization(value = "JWT") }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "수정 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "요청 값이 올바르지 않음"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "본인 소유의 스케줄이 아님"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "스케줄을 찾을 수 없음"),
            @io.swagger.annotations.ApiResponse(code = 409, message = "이미 해당 자녀에게 등록된 스케줄이 있음") })
    public ApiResponse<AllowanceScheduleResponseDTO> updateSchedule(
            @AuthenticationPrincipal MemberPrincipal principal,
            @ApiParam(value = "스케줄 아이디", required = true) @PathVariable Long id,
            @Valid @RequestBody AllowanceScheduleUpdateRequestDTO request) {
        AllowanceScheduleVO schedule = allowanceScheduleService.updateSchedule(
                principal, id, request.getChildId(), request.getAmount(),
                request.getCycleType(), request.getPaymentDay());
        return ApiResponse.ok(AllowanceScheduleResponseDTO.of(schedule));
    }

    @PatchMapping("/{id}/status")   // PATCH /allowance/schedule/5/status 같은 형태
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "정기 용돈 스케줄 활성화/비활성화",
            notes = "다시 활성화하면 다음 지급일이 오늘 기준으로 재계산됩니다.",
            authorizations = { @io.swagger.annotations.Authorization(value = "JWT") }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "변경 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "요청 값이 올바르지 않음"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "본인 소유의 스케줄이 아님"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "스케줄을 찾을 수 없음") })
    public ApiResponse<AllowanceScheduleResponseDTO> updateStatus(
            @AuthenticationPrincipal MemberPrincipal principal,
            @ApiParam(value = "스케줄 아이디", required = true) @PathVariable Long id,
            @Valid @RequestBody AllowanceScheduleStatusRequestDTO request) {
        AllowanceScheduleVO schedule = allowanceScheduleService.updateStatus(
                principal, id, request.getIsActive());
        return ApiResponse.ok(AllowanceScheduleResponseDTO.of(schedule));
    }

    @DeleteMapping("/{id}")   // DELETE /allowance/schedule/{id}
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "정기 용돈 스케줄 삭제",
            notes = "스케줄을 완전히 삭제합니다.",
            authorizations = { @io.swagger.annotations.Authorization(value = "JWT") }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "삭제 성공"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "본인 소유의 스케줄이 아님"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "스케줄을 찾을 수 없음") })
    public ApiResponse<Void> deleteSchedule(
                                               @AuthenticationPrincipal MemberPrincipal principal,
                                               @ApiParam(value = "스케줄 아이디", required = true) @PathVariable Long id) {
        allowanceScheduleService.deleteSchedule(principal, id);
        return ApiResponse.ok();
    }


}
