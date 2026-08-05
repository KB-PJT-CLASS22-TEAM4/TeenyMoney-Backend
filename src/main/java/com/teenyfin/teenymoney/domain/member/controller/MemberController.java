package com.teenyfin.teenymoney.domain.member.controller;

import com.teenyfin.teenymoney.domain.member.dto.response.MemberMeResponseDTO;
import com.teenyfin.teenymoney.domain.member.service.MemberService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
@Api(tags = "Members", description = "회원 정보 API")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/me")
    @ApiOperation(
            value = "내 회원정보 조회",
            notes = "Access Token으로 인증된 현재 회원의 정보를 조회합니다.",
            authorizations = {@io.swagger.annotations.Authorization(value = "JWT")})
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "조회 성공"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 토큰 없음 또는 만료"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "비활성 회원"),
            @io.swagger.annotations.ApiResponse(code = 404, message = "회원을 찾을 수 없음")
    })
    public ApiResponse<MemberMeResponseDTO> getMe(
            @AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.ok(memberService.getMe(principal.memberId()));
    }
}
