package com.teenyfin.teenymoney.domain.permission.controller;

import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseDTO;
import com.teenyfin.teenymoney.domain.permission.dto.response.PermissionResponseWrapperDTO;
import com.teenyfin.teenymoney.domain.permission.service.PermissionService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
