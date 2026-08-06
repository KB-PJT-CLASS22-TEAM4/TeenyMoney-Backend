package com.teenyfin.teenymoney.domain.family.controller;

import com.teenyfin.teenymoney.domain.family.dto.request.FamilyLinkRequestDTO;
import com.teenyfin.teenymoney.domain.family.dto.response.FamilyLinkCodeResponseDTO;
import com.teenyfin.teenymoney.domain.family.service.FamilyLinkCodeService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/families")
@Api(tags = "Families", description = "가족 연동 API")
public class FamilyController {

    private final FamilyLinkCodeService familyLinkCodeService;

    public FamilyController(FamilyLinkCodeService familyLinkCodeService) {
        this.familyLinkCodeService = familyLinkCodeService;
    }

    @PostMapping("/make-codes")
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "가족 연동 코드 발급",
            notes = "부모 회원에게 10분간 유효한 6자리 가족 연동 코드를 발급합니다. "
                    + "재발급하면 직전에 발급한 코드는 즉시 무효가 되며, "
                    + "부모당 유효한 코드는 항상 하나입니다.\n\n"
                    + "Idempotency-Key 헤더는 필수입니다. '발급 의도'마다 고유한 값(UUID)을 보내십시오. "
                    + "같은 키로 다시 호출하면 새 코드를 만들지 않고 그때 발급한 코드를 그대로 돌려줍니다. "
                    + "타임아웃 재시도에 같은 키를 재사용해야, 늦게 도착한 첫 응답이 "
                    + "이미 무효가 된 코드를 화면에 남기지 않습니다. "
                    + "사용자가 새 코드를 원할 때만 새 키를 만드십시오.",
            authorizations = {
                    @io.swagger.annotations.Authorization(value = "JWT")
            }
    )
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(
                    code = 200,
                    message = "연동 코드 발급 성공"
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 400,
                    message = "Idempotency-Key 헤더가 없거나 값이 올바르지 않음"
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 401,
                    message = "로그인이 필요함"
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 403,
                    message = "부모 회원이 아님"
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 429,
                    message = "직전 발급 직후라 잠시 후 재시도해야 함"
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 503,
                    message = "연동 코드를 발급할 수 없음"
            )
    })
    public ApiResponse<FamilyLinkCodeResponseDTO> makeLinkCode(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return ApiResponse.ok(
                familyLinkCodeService.makeCode(principal.memberId(), idempotencyKey)
        );
    }

    @PostMapping("/connect-link")
    @PreAuthorize("hasRole('CHILD')")
    @ApiOperation(
            value = "가족 연동 코드 소비",
            notes = "자녀가 부모의 6자리 연동 코드를 소비하고 가족 관계를 생성합니다.",
            authorizations = {
                    @io.swagger.annotations.Authorization(value = "JWT")
            }
    )
    public ApiResponse<Void> linkFamily(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody FamilyLinkRequestDTO request) {
        familyLinkCodeService.linkChild(principal.memberId(), request.getCode());
        return ApiResponse.ok();
    }


}
