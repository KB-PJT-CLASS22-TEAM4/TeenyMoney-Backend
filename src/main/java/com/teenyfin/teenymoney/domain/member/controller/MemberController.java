package com.teenyfin.teenymoney.domain.member.controller;

import com.teenyfin.teenymoney.domain.member.dto.response.MemberMeResponseDTO;
import com.teenyfin.teenymoney.domain.member.dto.response.MemberProfileImageResponseDTO;
import com.teenyfin.teenymoney.domain.member.service.MemberService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/me")
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
}
