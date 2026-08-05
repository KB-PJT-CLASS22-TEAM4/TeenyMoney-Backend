package com.teenyfin.teenymoney.domain.member.controller;

import com.teenyfin.teenymoney.domain.member.dto.response.MemberChildResponseDTO;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberMeResponseDTO;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberProfileImageResponseDTO;
import com.teenyfin.teenymoney.domain.member.service.MemberService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.http.MediaType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    /**
     * 프로필 이미지 변경.
     *
     * memberId는 요청 본문이 아니라 토큰에서 꺼낸다. 파라미터로 받으면 남의 프로필을
     * 바꿀 수 있다.
     */
    @PatchMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MemberProfileImageResponseDTO> updateProfileImage(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(
                memberService.updateProfileImage(principal.memberId(), file));
    }

    @GetMapping(value ="/me/children")
    @ApiOperation(
            value = "연동된 자녀 목록 조회",
            notes = "인증된 부모와 ACTIVE 상태로 연동된 자녀 목록을 조회합니다.",
            authorizations = {@io.swagger.annotations.Authorization(value = "JWT")})
    @ApiResponses({
            @io.swagger.annotations.ApiResponse(code = 200, message = "조회 성공"),
            @io.swagger.annotations.ApiResponse(code = 401, message = "인증 토큰 없음 또는 만료"),
            @io.swagger.annotations.ApiResponse(code = 403, message = "부모 회원이 아님")
    })
    public ApiResponse<List<MemberChildResponseDTO>> getMemberChild(
        @AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.ok(memberService.getChildren(principal));
    }
}
