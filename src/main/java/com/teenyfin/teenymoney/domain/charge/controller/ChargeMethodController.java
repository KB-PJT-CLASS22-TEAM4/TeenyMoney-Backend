package com.teenyfin.teenymoney.domain.charge.controller;

import com.teenyfin.teenymoney.domain.charge.dto.request.CardRegisterRequestDTO;
import com.teenyfin.teenymoney.domain.charge.dto.response.ChargeMethodResponseDTO;
import com.teenyfin.teenymoney.domain.charge.service.ChargeMethodService;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.teenyfin.teenymoney.global.response.ApiResponse;

import javax.validation.Valid;
import java.util.List;
// 컨트롤러는 얇게: 인증 정보 꺼내고, 요청 DTO를 서비스가 원하는 개별 파라미터로 풀어서
// 넘기고, 서비스가 리턴한 걸 ApiResponse로 감싸는 것만 함. 업무 로직은 전부 서비스에 있음.
@RestController
@RequestMapping("/charge-methods")
@Api(tags = "ChargeMethod", description = "결제수단(카드) 등록/조회/관리 API")
public class ChargeMethodController {

    private final ChargeMethodService chargeMethodService;


    public ChargeMethodController(ChargeMethodService chargeMethodService) {
        this.chargeMethodService = chargeMethodService;
    }
    @PostMapping("/card")
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "카드 결제수단 등록",
            notes = "카드 정보를 받아 토스페이먼츠에 빌링키 발급 요청하고, 발급된 빌링키를 암호화해 저장함.",
            authorizations = {
                    @io.swagger.annotations.Authorization(value = "JWT"),

            }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "등록 성공"),
            @io.swagger.annotations.ApiResponse(code = 400, message = "요청 값이 올바르지 않음"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "부모만 등록 가능"),
            @io.swagger.annotations.ApiResponse(code = 502, message = "토스페이먼츠 빌링키 발급 실패")})
    public ApiResponse<ChargeMethodResponseDTO> registerCard(@AuthenticationPrincipal MemberPrincipal principal,
                                                             @Valid @RequestBody CardRegisterRequestDTO request) {
        ChargeMethodResponseDTO response = chargeMethodService.registerCard(
                principal,
                request.getCardNumber(),request.getCardExpirationYear(),
                request.getCardExpirationMonth(),
                request.getCustomerIdentityNumber(),
                request.getCardPassword());

        return ApiResponse.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "결제수단 목록 조회",
            notes = "로그인한 부모가 등록한 결제수단 목록을 조회합니다.",
            authorizations = {
                    @io.swagger.annotations.Authorization(value = "JWT")
            }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "조회 성공"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "부모만 조회 가능")})
    public ApiResponse<List<ChargeMethodResponseDTO>> listChargeMethods(
            @AuthenticationPrincipal MemberPrincipal principal) {

        List<ChargeMethodResponseDTO> response = chargeMethodService.listChargeMethods(principal);
        return ApiResponse.ok(response);
    }

    @PatchMapping("/{id}/primary")
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "주거래 수단 지정",
            notes = "지정한 결제수단을 주거래 수단으로 설정합니다. 기존 주거래 수단은 자동으로 해제됩니다.",
            authorizations = {
                    @io.swagger.annotations.Authorization(value = "JWT")
            }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "지정 성공"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "본인의 결제수단만 지정 가능"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "결제수단을 찾을 수 없음")})
    public ApiResponse<Void> setPrimary(
            @AuthenticationPrincipal MemberPrincipal principal,
            @ApiParam(value = "결제수단 아이디", required = true) @PathVariable Long id) {

        chargeMethodService.setPrimary(principal, id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "결제수단 삭제",
            notes = "토스페이먼츠 빌링키를 폐기하고, 결제수단 상태를 INACTIVE로 변경합니다.",
            authorizations = {
                    @io.swagger.annotations.Authorization(value = "JWT")
            }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "삭제 성공"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "로그인 필요"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "본인의 결제수단만 삭제 가능"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "결제수단을 찾을 수 없음"),
            @io.swagger.annotations.ApiResponse(code = 502, message = "토스페이먼츠 빌링키 삭제 실패")})
    public ApiResponse<Void> deleteChargeMethod(
            @AuthenticationPrincipal MemberPrincipal principal,
            @ApiParam(value = "결제수단 아이디", required = true) @PathVariable Long id) {

        chargeMethodService.deleteChargeMethod(principal, id);
        return ApiResponse.ok();
    }

}
