package com.teenyfin.teenymoney.domain.categoryPolicy.controller;

import com.teenyfin.teenymoney.domain.categoryPolicy.dto.response.CategoryPolicyResponseDTO;
import com.teenyfin.teenymoney.domain.categoryPolicy.service.CategoryPolicyService;
import com.teenyfin.teenymoney.global.response.ApiResponse;
import com.teenyfin.teenymoney.global.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
public class CategoryPolicyController {

    private final CategoryPolicyService categoryPolicyService;

    // 전체 업종 카테고리 정책 조회
    @GetMapping
    public ApiResponse<List<CategoryPolicyResponseDTO>> getCategoryPolicy(@AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        return ApiResponse.ok(categoryPolicyService.getCategoryPolicy(memberPrincipal));
    }
}
