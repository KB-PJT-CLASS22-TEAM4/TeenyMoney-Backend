package com.teenyfin.teenymoney.domain.charge.controller;


import com.teenyfin.teenymoney.domain.charge.dto.request.ChargeRequestDTO;
import com.teenyfin.teenymoney.domain.charge.dto.response.ChargeResponseDTO;
import com.teenyfin.teenymoney.domain.charge.service.ChargeService;
import com.teenyfin.teenymoney.domain.charge.vo.ChargeVO;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.teenyfin.teenymoney.global.response.ApiResponse;

import javax.validation.Valid;

@RestController
@RequestMapping("/charge")
@Api(tags = "Charge", description = "카드 충전 실행 API")
public class ChargeController {

    private final ChargeService chargeService;

    public ChargeController(ChargeService chargeService) {
        this.chargeService = chargeService;
    }
    @PostMapping
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "카드로 지갑 충전",
            notes = "등록된 결제수단으로 토스페이먼츠에 실제 결제 승인을 요청하고, 성공하면 그 금액만큼 본인 지갑을 충전합니다.",
            authorizations = {
                    @io.swagger.annotations.Authorization(value = "JWT")
            }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "충전 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "요청 값이 올바르지 않음"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "부모만 충전 가능 / 본인 결제수단만 사용 가능"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "결제수단 또는 충전 시도를 찾을 수 없음"),
            @io.swagger.annotations.ApiResponse(code = 409, message = "이미 처리 중이거나 충돌하는 요청"),
            @io.swagger.annotations.ApiResponse(code = 502, message = "토스페이먼츠 승인 실패")})
    public ApiResponse<ChargeResponseDTO> charge(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody ChargeRequestDTO request) {

        // 1단계: PENDING으로 접수 (검증 + 중복 체크, 아직 토스 호출 없음)
        ChargeVO pending = chargeService.createPendingCharge(
                principal, request.getPaymentMethodId(), request.getAmount(), request.getIdempotencyKey());

        // 2단계: 실제로 토스 승인 + 지갑 크레딧까지 실행
        ChargeVO result = chargeService.executeCharge(pending.getId());

        return ApiResponse.ok(ChargeResponseDTO.of(result));
    }
}
