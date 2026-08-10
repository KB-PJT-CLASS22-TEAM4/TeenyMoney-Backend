package com.teenyfin.teenymoney.domain.allowance.controller;


import com.teenyfin.teenymoney.domain.allowance.dto.request.AllowanceSendRequestDTO;
import com.teenyfin.teenymoney.domain.allowance.dto.response.AllowanceSendResponseDTO;
import com.teenyfin.teenymoney.domain.allowance.service.AllowanceService;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.teenyfin.teenymoney.global.response.ApiResponse;

import javax.validation.Valid;

@RestController
@RequestMapping("/allowance")
@Api(tags = "Allowance", description = "용돈 보내기 API")
public class AllowanceController {

    private final AllowanceService allowanceService;

    public AllowanceController(AllowanceService allowanceService) {
        this.allowanceService = allowanceService;
    }

    @PostMapping("/children/{childId}")
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "자녀에게 용돈 보내기",
            notes = "부모가 연동된 자녀에게 1회성으로 용돈을 송금합니다.\n\n"
                    + "Idempotency-Key 헤더는 필수입니다. '보내기' 버튼 클릭 시점마다 새로운 값(UUID)을 "
                    + "보내십시오. 같은 키로 재시도하면 같은 송금 시도의 결과를 그대로 돌려줍니다.",
            authorizations = {
                    @io.swagger.annotations.Authorization(value = "JWT")
            }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "송금 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "금액이 올바르지 않음 또는 잔액 부족"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "접근 권한이 없음(자기 자신한테 송금 or 연동 안되어있는 자녀)"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "지갑을 찾을 수 없음"),
            @io.swagger.annotations.ApiResponse(code = 409, message = "이미 다른 내용으로 사용된 멱등성 키") })

    public ApiResponse<AllowanceSendResponseDTO> sendAllowance(
            @AuthenticationPrincipal MemberPrincipal principal,
            @ApiParam(value = "용돈을 받을 자녀의 회원 아이디", required = true) @PathVariable Long childId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AllowanceSendRequestDTO request) {

        AllowanceSendResponseDTO response = allowanceService.sendAllowance(
                principal, childId, request.getAmount(), idempotencyKey);
        return ApiResponse.ok(response);
    }
}
