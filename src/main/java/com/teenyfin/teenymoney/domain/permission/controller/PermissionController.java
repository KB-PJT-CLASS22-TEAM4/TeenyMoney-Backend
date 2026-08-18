package com.teenyfin.teenymoney.domain.permission.controller;

import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionUpdateRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionStatusResponseDTO;
import com.teenyfin.teenymoney.domain.permission.service.PermissionService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Api(tags = "Permission", description = "오늘만 허용 요청 API")
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @ApiOperation(value = "오늘만 허용 요청 조회", notes = "오늘 날짜에 생성된 오늘만 허용 요청을 조회합니다. 카테고리 단위로 정보를 반환합니다.")
    @GetMapping
    public ApiResponse<List<PermissionResponseDTO>> getPermission(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestParam(required = false) Long childId) {
        return ApiResponse.ok(permissionService.getPermission(memberPrincipal.memberId(), memberPrincipal.role(), childId));
    }

    @ApiOperation(value = "오늘만 허용 요청 현황 조회", notes = "이번 달 오늘만 허용을 요청한 일수, 앞으로 요청 가능한 일수와 카테고리별 오늘 기준 현재 상태(AVAILABLE/PENDING/APPROVED/REJECTED/EXPIRED)를 조회합니다.")
    @GetMapping("/status")
    public ApiResponse<PermissionStatusResponseDTO> getPermissionStatus(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestParam(required = false) Long childId) {
        return ApiResponse.ok(permissionService.getPermissionStatus(memberPrincipal.memberId(), memberPrincipal.role(), childId));
    }

    @ApiOperation(value = "오늘만 허용 요청", notes = "원하는 카테고리와 사유를 포함해 오늘만 허용을 요청합니다.")
    @PostMapping
    public ApiResponse<List<PermissionResponseDTO>> createPermission(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @ApiParam(value = "오늘만 허용을 요청할 카테고리 ID와 요청 사유", required = true) @RequestBody @Valid PermissionRequestDTO permissionRequestDTO) {
        return ApiResponse.ok(permissionService.createPermission(memberPrincipal.memberId(), memberPrincipal.role(), permissionRequestDTO));
    }

    @ApiOperation(value = "오늘만 허용 요청 내용 수정", notes = "아직 승인 혹은 거절되지 않은 오늘만 허용 요청의 사유를 수정합니다.")
    @PatchMapping("/{permissionId}")
    public ApiResponse<List<PermissionResponseDTO>> updatePermission(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @PathVariable Long permissionId,
            @ApiParam(value = "오늘만 허용을 요청할 카테고리 ID와 요청 사유", required = true) @RequestBody @Valid PermissionUpdateRequestDTO permissionUpdateRequestDTO) {
        return ApiResponse.ok(permissionService.updatePermission(memberPrincipal.memberId(), memberPrincipal.role(), permissionId, permissionUpdateRequestDTO));
    }

    @ApiOperation(value = "오늘만 허용 요청 승인", notes = "아직 승인 혹은 거절되지 않은 오늘만 허용 요청을 승인합니다.")
    @PatchMapping("/{permissionId}/approve")
    public ApiResponse<List<PermissionResponseDTO>> approvePermission(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @PathVariable Long permissionId) {
        return ApiResponse.ok(permissionService.approvePermission(memberPrincipal.memberId(), memberPrincipal.role(), permissionId));
    }

    @ApiOperation(value = "오늘만 허용 요청 거절", notes = "아직 승인 혹은 거절되지 않은 오늘만 허용 요청을 거절합니다.")
    @PatchMapping("/{permissionId}/reject")
    public ApiResponse<List<PermissionResponseDTO>> rejectPermission(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @PathVariable Long permissionId) {
        return ApiResponse.ok(permissionService.rejectPermission(memberPrincipal.memberId(), memberPrincipal.role(), permissionId));
    }

    @ApiOperation(value = "오늘만 허용 요청 취소", notes = "아직 승인 혹은 거절되지 않은 오늘만 허용 요청을 취소합니다.")
    @DeleteMapping("/{permissionId}")
    public ApiResponse<Void> deletePermission(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @PathVariable Long permissionId) {
        permissionService.deletePermission(memberPrincipal.memberId(), memberPrincipal.role(), permissionId);
        return ApiResponse.ok();
    }
}
