package com.teenyfin.teenymoney.domain.permission.controller;

import com.teenyfin.teenymoney.domain.permission.dto.request.PermissionRequestDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseWrapperDTO;
import com.teenyfin.teenymoney.domain.permission.service.PermissionService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @ApiOperation(value = "오늘만 허용 요청 조회", notes = "오늘 날짜에 생성된 오늘만 허용 요청을 조회합니다.")
    @GetMapping
    public ApiResponse<PermissionResponseWrapperDTO> getPermission(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        return ApiResponse.ok(permissionService.getPermission(memberPrincipal.memberId(), memberPrincipal.role()));
    }

    @ApiOperation(value = "오늘만 허용 요청", notes = "원하는 카테고리와 사유를 포함해 오늘만 허용을 요청합니다.")
    @PostMapping
    public ApiResponse<PermissionResponseWrapperDTO> createPermission(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestBody @Valid PermissionRequestDTO permissionRequestDTO) {
        return ApiResponse.ok(permissionService.createPermission(memberPrincipal.memberId(), memberPrincipal.role(), permissionRequestDTO));
    }

    @ApiOperation(value = "오늘만 허용 요청 내용 수정", notes = "아직 수락 혹은 거절되지 않은 오늘만 허용 요청의 카테고리와 사유를 수정합니다.")
    @PatchMapping("/{permissionId}")
    public ApiResponse<PermissionResponseWrapperDTO> updatePermission(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @PathVariable Long permissionId,
            @RequestBody @Valid PermissionRequestDTO permissionRequestDTO) {
        return ApiResponse.ok(permissionService.updatePermission(memberPrincipal.memberId(), memberPrincipal.role(), permissionId, permissionRequestDTO));
    }
}
