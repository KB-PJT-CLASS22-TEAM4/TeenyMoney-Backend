package com.teenyfin.teenymoney.domain.family.controller;

import com.teenyfin.teenymoney.domain.family.dto.response.FamilyLinkCodeResponseDTO;
import com.teenyfin.teenymoney.domain.family.service.FamilyLinkCodeService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/families")
@Api(
        tags = "Families",
        description = "가족 연동 API"
)
public class FamilyController {

    private final FamilyLinkCodeService familyLinkCodeService;

    public FamilyController(
            FamilyLinkCodeService familyLinkCodeService
    ) {
        this.familyLinkCodeService = familyLinkCodeService;
    }

    @PostMapping("/link-codes")
    @PreAuthorize("hasRole('PARENT')")
    @ApiOperation(
            value = "가족 연동 코드 발급",
            notes = "부모 회원에게 10분간 유효한 6자리 가족 연동 코드를 발급합니다.",
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
                    code = 401,
                    message = "로그인이 필요함"
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 403,
                    message = "부모 회원이 아님"
            ),
            @io.swagger.annotations.ApiResponse(
                    code = 503,
                    message = "연동 코드를 발급할 수 없음"
            )
    })
    public ApiResponse<FamilyLinkCodeResponseDTO> issueLinkCode(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ApiResponse.ok(
                familyLinkCodeService.makeCode(principal.memberId())
        );
    }
}